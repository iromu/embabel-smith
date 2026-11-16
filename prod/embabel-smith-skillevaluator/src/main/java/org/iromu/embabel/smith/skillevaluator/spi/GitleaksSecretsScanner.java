/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.spi;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import org.iromu.embabel.smith.skillevaluator.model.Finding;
import org.iromu.embabel.smith.skillevaluator.model.Severity;
import org.iromu.embabel.smith.skillevaluator.model.ValidationResult;
import org.iromu.embabel.smith.skillevaluator.util.Constants;

/**
 * Built-in {@link SecretsScanner}: a faithful driver of the Gitleaks CLI, ported from
 * {@code validators/secrets.py}.
 *
 * <h2>What this port is</h2>
 *
 * Upstream performs no detection of its own — {@code secrets.py} writes a TOML config,
 * shells out to {@code gitleaks detect}, and shapes the JSON report into findings. This
 * port keeps that contract exactly: the config bytes, the argv, the exit-code triage,
 * every category / check name / message / suggestion and the severity map are
 * byte-identical to the Python source.
 *
 * <h2>Documented deviations</h2>
 *
 * <ul>
 * <li><b>Install hint.</b> The "gitleaks not installed" warning keeps the upstream text
 * verbatim ({@code brew install gitleaks} / {@code go install ...}). It is still the
 * right advice from a Java host — Gitleaks is a standalone Go binary — so the string was
 * not adapted to Maven idioms; changing it would break parity with upstream reports.</li>
 * <li><b>Managed provisioning.</b> Upstream may auto-download a pinned Gitleaks binary;
 * see {@link SecretsGitleaksCli}. A missing binary reports unavailable and the scan is
 * marked incomplete.</li>
 * <li><b>Where the logic lives.</b> {@code _determine_severity},
 * {@code _process_findings} and {@code _create_gitleaks_config} are private methods on
 * the Python validator. Here they are package-private statics on this engine, because the
 * engine (not the validator shell) is what owns the Gitleaks interaction once the scanner
 * became pluggable; their visibility matches upstream.</li>
 * <li><b>Path shaping.</b> Like Python, the finding's {@code file_path} is the raw
 * {@code File} value Gitleaks reported — no relativisation happens in this class.
 * Relativity comes from the {@code --source .} + {@code cwd=<skill>} convention, which is
 * why that convention is preserved verbatim.</li>
 * <li><b>Secret redaction.</b> Upstream never copies {@code Secret} or {@code Match} into
 * the result; the finding carries only the rule description, path, line and rule id. That
 * is the redaction guarantee, and it is preserved (asserted by the tests).</li>
 * </ul>
 */
public final class GitleaksSecretsScanner implements SecretsScanner {

	/**
	 * Scanner name recorded via {@link ValidationResult#recordScanner(String, String)}.
	 */
	public static final String SCANNER_NAME = "gitleaks";

	/**
	 * Gitleaks normally uses exit 1 for both findings and operational errors. Every
	 * invocation overrides the findings code so those states are distinct.
	 */
	public static final int GITLEAKS_FINDINGS_EXIT_CODE = 10;

	/** Python {@code SecretsValidator._NVIDIA_RULES}, verbatim. */
	static final String NVIDIA_RULES = "\n" + "[[rules]]\n" + "id = \"nvidia-ngc-api-key\"\n"
			+ "description = \"NVIDIA NGC API Key\"\n" + "regex = '''\\$oauthtoken:\\S{84}'''\n"
			+ "tags = [\"nvidia\", \"ngc\", \"api-key\"]\n" + "\n" + "[[rules]]\n" + "id = \"nvidia-api-key\"\n"
			+ "description = \"NVIDIA API Key\"\n" + "regex = '''nvapi-[A-Za-z0-9_-]{40,}'''\n"
			+ "tags = [\"nvidia\", \"api-key\"]\n";

	/**
	 * Python {@code _create_gitleaks_config}: everything before the artifact-dir path
	 * list.
	 */
	static final String ALLOWLIST_HEAD = "\n" + "[extend]\n" + "useDefault = true\n" + "\n" + "[allowlist]\n"
			+ "description = \"SkillEvaluator allowlist\"\n" + "paths = [\n"
			+ "    '''(^|/)(tests?|examples?|fixtures?|mocks?)(/|$)''',\n";

	/**
	 * Python {@code _create_gitleaks_config}: everything after the artifact-dir path
	 * list.
	 */
	static final String ALLOWLIST_TAIL = "\n" + "]\n" + "regexes = [\n" + "    '''YOUR_.*_HERE''',\n"
			+ "    '''INSERT_.*_HERE''',\n" + "    '''PLACEHOLDER''',\n" + "    '''example\\.com''',\n"
			+ "    '''test\\.nvidia\\.com''',\n" + "    '''dummy''',\n" + "    '''fake''',\n" + "]\n" + "\n";

	/**
	 * Python {@code SecretsValidator._SEVERITY_KEYWORDS}: iteration order is significant.
	 */
	static final Map<String, Severity> SEVERITY_KEYWORDS = buildSeverityKeywords();

	private static final ObjectMapper MAPPER = new ObjectMapper().enable(DeserializationFeature.USE_LONG_FOR_INTS);

	/**
	 * Python 3.7+ {@code re.escape}: only these ASCII characters are backslash-escaped.
	 */
	private static final String PYTHON_RE_SPECIALS = "()[]{}?*+-|^$\\.&~# \t\n\r\f";

	private static Map<String, Severity> buildSeverityKeywords() {
		Map<String, Severity> keywords = new LinkedHashMap<>();
		keywords.put("key", Severity.CRITICAL);
		keywords.put("secret", Severity.CRITICAL);
		keywords.put("token", Severity.CRITICAL);
		keywords.put("password", Severity.CRITICAL);
		keywords.put("credential", Severity.CRITICAL);
		keywords.put("private-key", Severity.CRITICAL);
		keywords.put("api", Severity.HIGH);
		keywords.put("auth", Severity.HIGH);
		keywords.put("generic", Severity.MEDIUM);
		return keywords;
	}

	private final SecretsScannerTool tool;

	private final boolean scanGitHistory;

	public GitleaksSecretsScanner() {
		this(new SecretsGitleaksCli(), false);
	}

	/**
	 * Python {@code SecretsValidator(scan_git_history=True)} is reachable through this
	 * constructor.
	 */
	public GitleaksSecretsScanner(boolean scanGitHistory) {
		this(new SecretsGitleaksCli(), scanGitHistory);
	}

	/** Test seam: run the same driver logic against a faked Gitleaks CLI. */
	public GitleaksSecretsScanner(SecretsScannerTool tool) {
		this(tool, false);
	}

	public GitleaksSecretsScanner(SecretsScannerTool tool, boolean scanGitHistory) {
		this.tool = tool;
		this.scanGitHistory = scanGitHistory;
	}

	public boolean isScanGitHistory() {
		return scanGitHistory;
	}

	@Override
	public String getName() {
		return SCANNER_NAME;
	}

	@Override
	public boolean isAvailable() {
		return tool.isAvailable();
	}

	@Override
	public ValidationResult scan(Path skillPath) {
		ValidationResult result = new ValidationResult();

		if (!tool.isAvailable()) {
			result.addWarning("gitleaks not installed - secrets scanning skipped. " + tool.installHint());
			result.markScanIncomplete(SCANNER_NAME);
			return result;
		}

		Path scanRoot = resolve(skillPath);
		String source;
		Path cwd;
		if (scanGitHistory) {
			source = scanRoot.toString();
			cwd = null;
		}
		else {
			// Keep no-git finding paths relative to the requested scan root so
			// allowlists cannot match unrelated absolute ancestors. Direct
			// files run from their parent, with "./" protecting option-like
			// filenames while preserving Gitleaks' native ignore semantics.
			boolean scanIsDir = Files.isDirectory(scanRoot);
			source = scanIsDir ? "." : "./" + scanRoot.getFileName();
			cwd = scanIsDir ? scanRoot : scanRoot.getParent();
		}

		Path configPath = createTempConfig(buildConfig());
		try {
			List<String> args = new ArrayList<>(List.of("detect", "--source", source, "--report-format", "json",
					"--report-path", "-", "--exit-code", String.valueOf(GITLEAKS_FINDINGS_EXIT_CODE), "--config",
					configPath.toString()));
			if (!scanGitHistory) {
				args.add("--no-git");
			}

			SecretsScannerTool.RunResult toolResult = tool.run(args, cwd);

			if (toolResult.errorMessage() != null) {
				String hint = toolResult.errorMessage().startsWith("couldn't ") ? tool.installHint() : "";
				result.addWarning(rstripDotSpace(toolResult.errorMessage() + ". " + hint));
				result.markScanIncomplete(SCANNER_NAME);
				return result;
			}
			result.recordScanner(SCANNER_NAME, tool.version());

			// The custom findings code disambiguates findings from Gitleaks'
			// default exit 1, which is also used for operational failures.
			if (toolResult.exitCode() == 0) {
				if (toolResult.stdout().strip().isEmpty()) {
					result.addSuccess("gitleaks", "No secrets detected by Gitleaks");
				}
				else {
					JsonNode findings = parseJsonOutput(toolResult.stdout());
					if (isEmptyJsonArray(findings)) {
						result.addSuccess("gitleaks", "No secrets detected by Gitleaks");
					}
					else if (isNonEmptyObjectList(findings)) {
						processFindings(findings, result);
						result.addError(
								"Gitleaks returned findings with clean exit code 0; scanner status is inconsistent");
						result.markScanIncomplete(SCANNER_NAME);
					}
					else {
						result.addError("Gitleaks clean exit did not return a valid empty JSON report");
						result.markScanIncomplete(SCANNER_NAME);
					}
				}
			}
			else if (toolResult.exitCode() == GITLEAKS_FINDINGS_EXIT_CODE) {
				JsonNode findings = parseJsonOutput(toolResult.stdout());
				if (isNonEmptyObjectList(findings)) {
					processFindings(findings, result);
				}
				else {
					result.addError("Gitleaks findings exit did not return a valid nonempty JSON report");
					result.markScanIncomplete(SCANNER_NAME);
				}
			}
			else {
				String error = toolResult.stderr().strip().isEmpty() ? "scanner output redacted"
						: truncate(toolResult.stderr().strip(), 200);
				result.addError("Gitleaks error (exit code " + toolResult.exitCode() + "): " + error);
				result.markScanIncomplete(SCANNER_NAME);
			}
		}
		finally {
			deleteQuietly(configPath);
		}

		return result;
	}

	// ------------------------------------------------------------- config

	/**
	 * Python {@code _create_gitleaks_config}: the allowlist plus the NVIDIA rules.
	 *
	 * <p>
	 * Tier 1 artifact dirs ({@code evals/}, {@code results/}, {@code versions/}, ...)
	 * hold Tier 3 output snapshots that routinely carry harvested test credentials;
	 * Gitleaks has to skip them via config since it takes no exclude flag.
	 *
	 * <p>
	 * Test/example/fixture/mock skips are directory path components, not substrings —
	 * {@code (.*)?test(.*)?} would also skip {@code latest} and {@code attestation}.
	 */
	static String buildConfig() {
		StringBuilder artifactPaths = new StringBuilder();
		int index = 0;
		for (String name : new TreeSet<>(Constants.SCAN_EXCLUDED_DIRS)) {
			if (index++ > 0) {
				artifactPaths.append('\n');
			}
			artifactPaths.append("    '''(^|/)").append(pythonReEscape(name)).append("(/|$)''',");
		}
		return ALLOWLIST_HEAD + artifactPaths + ALLOWLIST_TAIL + NVIDIA_RULES;
	}

	/** Python {@code create_temp_config(content, suffix=".toml")}: caller cleans up. */
	static Path createTempConfig(String content) {
		try {
			Path configPath = Files.createTempFile("skillevaluator_", ".toml");
			Files.writeString(configPath, content, StandardCharsets.UTF_8);
			return configPath;
		}
		catch (IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}

	/**
	 * Python 3.7+ {@code re.escape}: escapes exactly the special set, leaving
	 * alphanumerics alone.
	 */
	static String pythonReEscape(String text) {
		StringBuilder out = new StringBuilder(text.length() + 4);
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (PYTHON_RE_SPECIALS.indexOf(c) >= 0) {
				out.append('\\');
			}
			out.append(c);
		}
		return out.toString();
	}

	// --------------------------------------------------------- report shaping

	/**
	 * Python {@code _process_findings}: report an already validated, nonempty Gitleaks
	 * findings list. Package-private: the conversion is unit-tested from this package
	 * (see {@code GitleaksSecretsScannerTest}) so nothing here is widened for test
	 * access.
	 */
	static void processFindings(JsonNode findings, ValidationResult result) {
		result.addMessage("Gitleaks found " + findings.size() + " potential secret(s)");

		for (JsonNode finding : findings) {
			Finding converted = convertFinding(finding);
			// Python: is_error=severity.is_error()
			result.addStructuredFinding(converted, converted.getSeverity().isError());
		}
	}

	/**
	 * Python {@code Finding(category="SECRET", ...)} construction inside
	 * {@code _process_findings}.
	 */
	static Finding convertFinding(JsonNode finding) {
		String ruleId = textOr(finding.get("RuleID"), "unknown");
		String description = textOr(finding.get("Description"), "Secret detected");
		String filePath = textOr(finding.get("File"), "unknown");
		int line = finding.get("StartLine") != null && finding.get("StartLine").isNumber()
				? finding.get("StartLine").intValue() : 0;

		String message = description + " in " + filePath + ":" + line + " [" + ruleId + "]";
		// Deliberately no Secret/Match: the reported value must never reach a report.
		return new Finding("SECRET", determineSeverity(finding), ruleId, message, filePath, line, null,
				"Remove the secret from " + filePath + ", revoke it, and read it from an environment variable", null);
	}

	/** Python {@code _determine_severity(rule_id, tags)} for one report entry. */
	static Severity determineSeverity(JsonNode finding) {
		List<String> tags = new ArrayList<>();
		JsonNode tagsNode = finding.get("Tags");
		if (tagsNode != null && tagsNode.isArray()) {
			for (JsonNode tag : tagsNode) {
				tags.add(tag.isTextual() ? tag.textValue() : String.valueOf(tag));
			}
		}
		return determineSeverity(finding.get("RuleID") != null && finding.get("RuleID").isTextual()
				? finding.get("RuleID").textValue() : "unknown", tags);
	}

	/**
	 * Python {@code _determine_severity}: tags first (more specific), then the rule id,
	 * else HIGH.
	 */
	static Severity determineSeverity(String ruleId, List<String> tags) {
		for (String tag : tags) {
			Severity severity = keywordSeverity(tag.toLowerCase(Locale.ROOT));
			if (severity != null) {
				return severity;
			}
		}
		Severity fromRule = keywordSeverity(ruleId.toLowerCase(Locale.ROOT));
		return fromRule != null ? fromRule : Severity.HIGH;
	}

	private static Severity keywordSeverity(String haystack) {
		for (Map.Entry<String, Severity> entry : SEVERITY_KEYWORDS.entrySet()) {
			if (haystack.contains(entry.getKey())) {
				return entry.getValue();
			}
		}
		return null;
	}

	// ------------------------------------------------------------- jvm helpers

	/**
	 * Port of {@code parse_json_output}: parsed JSON or null when it cannot be parsed.
	 */
	static JsonNode parseJsonOutput(String output) {
		if (output == null || output.isBlank()) {
			return null;
		}
		try {
			return MAPPER.readTree(output);
		}
		catch (IOException e) {
			return null;
		}
	}

	/** Python {@code findings == []}. */
	private static boolean isEmptyJsonArray(JsonNode node) {
		return node != null && node.isArray() && node.isEmpty();
	}

	/**
	 * Python
	 * {@code isinstance(findings, list) and findings and all(isinstance(i, dict) ...)}.
	 */
	private static boolean isNonEmptyObjectList(JsonNode node) {
		if (node == null || !node.isArray() || node.isEmpty()) {
			return false;
		}
		for (JsonNode item : node) {
			if (!item.isObject()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Python {@code Path.resolve()}: absolute, symlinks resolved, normalised; falls back
	 * when it cannot.
	 */
	private static Path resolve(Path path) {
		Path absolute = path.toAbsolutePath().normalize();
		try {
			return absolute.toRealPath();
		}
		catch (IOException e) {
			// Python resolve(strict=False) keeps a path that does not exist yet.
			return absolute;
		}
	}

	/** Python {@code str.rstrip(". ")}: trim dots and spaces from the end. */
	static String rstripDotSpace(String text) {
		int end = text.length();
		while (end > 0 && (text.charAt(end - 1) == '.' || text.charAt(end - 1) == ' ')) {
			end--;
		}
		return text.substring(0, end);
	}

	/** Python code-point truncation ({@code s[:limit]}). */
	static String truncate(String text, int limit) {
		if (text == null) {
			return "";
		}
		if (text.codePointCount(0, text.length()) <= limit) {
			return text;
		}
		return text.substring(0, text.offsetByCodePoints(0, limit));
	}

	/**
	 * Python {@code dict.get(key, default)}: the default applies only to a MISSING key.
	 */
	private static String textOr(JsonNode node, String fallback) {
		if (node == null || node.isMissingNode()) {
			return fallback;
		}
		if (node.isNull()) {
			return "None";
		}
		if (node.isTextual()) {
			return node.textValue();
		}
		if (node.isNumber()) {
			return String.valueOf(node.numberValue());
		}
		if (node.isBoolean()) {
			return node.booleanValue() ? "True" : "False";
		}
		return node.toString();
	}

	private static void deleteQuietly(Path path) {
		try {
			Files.deleteIfExists(path);
		}
		catch (IOException ignored) {
			// Python unlink(missing_ok=True): a leftover temp config is not a scan
			// failure.
		}
	}

}
