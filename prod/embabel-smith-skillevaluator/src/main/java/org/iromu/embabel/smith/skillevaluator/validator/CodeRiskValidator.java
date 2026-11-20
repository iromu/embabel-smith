/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.validator;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import org.iromu.embabel.smith.skillevaluator.model.Finding;
import org.iromu.embabel.smith.skillevaluator.model.Severity;
import org.iromu.embabel.smith.skillevaluator.model.ValidationResult;
import org.iromu.embabel.smith.skillevaluator.util.Constants;
import org.iromu.embabel.smith.skillevaluator.util.ScanPaths;
import org.iromu.embabel.smith.skillevaluator.util.ToolRunner;

/**
 * Static code analysis for security risks, ported from the Python
 * {@code validators/code_risk.py}.
 * <p>
 * Combines Bandit's Python checks with a packaged, high-confidence Semgrep baseline for
 * command/dynamic-code execution, Python SQL interpolation, and download-to-shell
 * patterns.
 * <h2>What this port is</h2> Upstream performs no analysis of its own:
 * {@code code_risk.py} is a faithful driver of the two external CLIs (Bandit and Semgrep)
 * plus their JSON-report validation, severity mapping and finding shaping. This port
 * keeps that contract exactly — every category, check name, severity, message and
 * suggestion string is byte-identical to the Python source, and the scanners are invoked
 * through {@link ToolRunner} exactly as the Python {@code ExternalTool} does. There is no
 * Python-AST rule logic in this validator to approximate; the PythonSyntax helper is
 * therefore unused here.
 * <h2>Documented approximations vs. the Python tool layer</h2>
 * <ul>
 * <li><b>Executable resolution.</b> Python's {@code ExternalTool} prefers a binary
 * installed next to its own interpreter (pipx/uv layouts), then {@code PATH}. Java has no
 * interpreter sibling, so resolution is the {@code SKILLEVALUATOR_BANDIT_PATH} /
 * {@code SKILLEVALUATOR_SEMGREP_PATH} override (validated fail-closed exactly like
 * Python: it must name an absolute executable file, else the tool reports unavailable
 * with the same configuration-error hint) followed by a plain {@code PATH} scan. Version
 * detection is the same {@code \d+\.\d+(\.\d+)?} search over the {@code --version}
 * output.</li>
 * <li><b>Code-point truncation.</b> Python slices strings by code point
 * ({@code msg[:100]}, {@code error_msg[:200]}); Java truncates with
 * {@link String#substring(int)} on UTF-16 units, so every Python slice here goes through
 * {@link #truncate(String, int)} which counts code points.</li>
 * <li><b>Filesystem case semantics.</b> Python's {@code matches_filesystem_name} confirms
 * a differently-cased spelling is the same physical inode before treating it as a
 * generated-file alias. {@code semgrepFileExcludes} reproduces that with the
 * {@code basic:fileKey} attribute instead of {@code lstat} inode numbers, so an authored
 * lowercase {@code benchmark.md} on a case-sensitive filesystem is not excluded as a
 * {@code BENCHMARK.md} alias — matching the upstream test's case-sensitive branch
 * exactly.</li>
 * <li><b>Folder-merge metadata.</b> Python's {@code merge_with_prefix} prefixes every
 * entry of {@code semgrep_unparsed_files} with the skill name. The frozen Java
 * {@code ValidationResult.mergeWithPrefix} does not carry that special case, so after a
 * folder merge the unparsed-file list keeps the per-skill relative paths instead of
 * {@code [skill] path} entries.</li>
 * <li><b>Packaged Semgrep rules.</b> Python reads {@code CONFIG_DIR /
 * "semgrep_rules.yaml"} from the installed package. The Java equivalent is the classpath
 * resource {@code org/skillevaluator/config/semgrep_rules.yaml} (copied verbatim from
 * upstream's config dir): a {@code file:} resource is used in place, any other scheme is
 * copied to a temp file. If the resource is not on the classpath, resolution falls back
 * to a conventional {@code <user home>/.skillevaluator/config/semgrep_rules.yaml}
 * location so operators can supply it; Semgrep itself then reports the missing file and
 * the scan is marked incomplete, exactly like a broken Python config path.</li>
 * </ul>
 *
 * @author Ivan Rodriguez
 */
public class CodeRiskValidator extends ValidatorBase {

	/**
	 * Environment variable that turns "Semgrep missing" from a skip into an incomplete
	 * scan.
	 */
	public static final String REQUIRE_SEMGREP_ENV = "SKILLEVALUATOR_REQUIRE_SEMGREP";

	/** Classpath location of the packaged Semgrep rules (upstream {@code CONFIG_DIR}). */
	static final String SEMGREP_RULES_RESOURCE = "org/iromu/embabel/smith/skillevaluator/config/semgrep_rules.yaml";

	/**
	 * Optional operator override for the rules file (Python has no equivalent; documented
	 * addition).
	 */
	static final String SEMGREP_RULES_ENV = "SKILLEVALUATOR_SEMGREP_RULES";

	private static final Set<String> SEMGREP_PARSE_ERROR_TYPES = Set.of("SyntaxError", "PartialParsing", "LexicalError",
			"ParseError", "OtherParseError", "AstBuilderError");

	private static final int SEMGREP_PARSE_ERROR_PATH_LIMIT = 20;

	private static final ObjectMapper MAPPER = new ObjectMapper().enable(DeserializationFeature.USE_LONG_FOR_INTS);

	private static final Pattern VERSION_PATTERN = Pattern.compile("\\d+\\.\\d+(?:\\.\\d+)?");

	/** Python fallback operand for ``error.get("path") or "unknown"``. */
	private static final JsonNode DEFAULT_UNKNOWN_PATH = MAPPER.getNodeFactory().textNode("unknown");

	private final boolean useSemgrep;

	private final boolean failOnLow;

	private final boolean excludeTests;

	private final ScannerTool banditTool;

	private final ScannerTool semgrepTool;

	private Path semgrepRulesPath;

	public CodeRiskValidator() {
		this(true, false, true);
	}

	public CodeRiskValidator(boolean useSemgrep, boolean failOnLow, boolean excludeTests) {
		this(useSemgrep, failOnLow, excludeTests,
				new CliTool("Bandit", "bandit", "SKILLEVALUATOR_BANDIT_PATH", banditInstallHint()),
				new CliTool("Semgrep", "semgrep", "SKILLEVALUATOR_SEMGREP_PATH", semgrepInstallHint()));
	}

	/**
	 * Test seam: run the same rule logic against faked Bandit/Semgrep CLIs.
	 * @param useSemgrep run the packaged Semgrep rules in addition to Bandit
	 * @param failOnLow retained for API parity; stored but never consulted, mirroring
	 * upstream's ``fail_on_low``
	 * @param excludeTests skip test directories and files (tests, test, *_test.py,
	 * test_*.py)
	 * @param bandit the Bandit CLI to invoke (the injected test seam)
	 * @param semgrep the Semgrep CLI to invoke (the injected test seam)
	 */
	CodeRiskValidator(boolean useSemgrep, boolean failOnLow, boolean excludeTests, ScannerTool bandit,
			ScannerTool semgrep) {
		this.useSemgrep = useSemgrep;
		// Mirrors upstream: the constructor stores fail_on_low but no code path in
		// code_risk.py consults it — is_error comes from severity/confidence only.
		this.failOnLow = failOnLow;
		this.excludeTests = excludeTests;
		this.banditTool = bandit;
		this.semgrepTool = semgrep;
	}

	public boolean isUseSemgrep() {
		return this.useSemgrep;
	}

	public boolean isFailOnLow() {
		return this.failOnLow;
	}

	public boolean isExcludeTests() {
		return this.excludeTests;
	}

	@Override
	public String getName() {
		return "Code Risk Analysis";
	}

	@Override
	public String getDescription() {
		return "Static code analysis using Bandit and packaged Semgrep rules";
	}

	@Override
	public ValidationResult validate(Path skillPath) {
		ValidationResult result = validateFolderOrSkill(skillPath, this::validateSingleSkill,
				"Analyzing code risk for");
		result.setValidatorName(getName());
		result.setValidatorDescription(getDescription());
		return result;
	}

	private ValidationResult validateSingleSkill(Path skillPath) {
		ValidationResult result = new ValidationResult();

		Map<String, Object> fileCounts = countCodeFiles(skillPath);
		if (asInt(fileCounts.get("py")) == 0 && asInt(fileCounts.get("sh")) == 0 && asInt(fileCounts.get("js")) == 0) {
			result.addSuccess("code_file_discovery", "No code files found - code risk analysis is not applicable",
					Map.of("file_counts", fileCounts));
			return result;
		}

		result.addMessage("Found " + fileCounts.get("py") + " Python, " + fileCounts.get("sh") + " Shell, "
				+ fileCounts.get("js") + " JavaScript/TypeScript files");

		if (asInt(fileCounts.get("py")) > 0) {
			result.merge(runBandit(skillPath));
		}

		if (this.useSemgrep) {
			result.merge(runSemgrep(skillPath));
		}

		return result;
	}

	/**
	 * Count code files by type.
	 *
	 * <p>
	 * Files under Tier 1 artifact directories ({@code evals/}, {@code results/},
	 * {@code versions/} and dot-prefixed variants) are excluded so that evaluation
	 * snapshots do not skew the counts that decide whether bandit / semgrep should run.
	 * @param skillPath skill directory whose scannable files are counted
	 * @return counts keyed ``py``, ``sh`` and ``js``, the last covering the
	 * JavaScript/TypeScript extensions
	 */
	private Map<String, Object> countCodeFiles(Path skillPath) {
		Map<String, Object> counts = new LinkedHashMap<>();
		counts.put("py", ScanPaths.iterScannableFiles(skillPath, Set.of(".py")).size());
		counts.put("sh", ScanPaths.iterScannableFiles(skillPath, Set.of(".sh")).size());
		counts.put("js",
				ScanPaths
					.iterScannableFiles(skillPath, Set.of(".js", ".jsx", ".mjs", ".cjs", ".ts", ".tsx", ".mts", ".cts"))
					.size());
		return counts;
	}

	// ------------------------------------------------------------------ bandit

	ValidationResult runBandit(Path skillPath) {
		ValidationResult result = new ValidationResult();

		if (!this.banditTool.isAvailable()) {
			result.addWarning("bandit not installed. " + banditInstallHint());
			result.markScanIncomplete("bandit");
			return result;
		}

		// Tier 1 artifact dirs (evals/, results/, versions/, ...) hold
		// snapshot copies of the live skill; scanning them produces
		// duplicate findings. Bandit takes a single comma-separated
		// ``--exclude`` arg that combines the test exclusion (when on)
		// with the artifact exclusion list.
		List<String> excludes = new ArrayList<>();
		if (this.excludeTests) {
			excludes.addAll(List.of("tests", "test", "*_test.py", "test_*.py"));
		}
		excludes.addAll(new TreeSet<>(Constants.SCAN_EXCLUDED_DIRS));

		// toAbsolutePath/normalize so a skill dir named like an option token (e.g.
		// "--exclude=*") cannot be parsed as a scanner flag. Mirrors resolve().
		List<String> args = new ArrayList<>(
				List.of("-r", skillPath.toAbsolutePath().normalize().toString(), "-f", "json", "-q", "-ll"));
		args.addAll(List.of("--exclude", String.join(",", excludes)));

		ScannerTool.RunResult toolResult = this.banditTool.run(args);

		if (toolResult.errorMessage() != null) {
			result.addWarning(toolResult.errorMessage());
			result.markScanIncomplete("bandit");
		}
		else if (toolResult.exitCode() == 0 || toolResult.exitCode() == 1) {
			// Exit code 0 = clean, 1 = findings; both emit JSON on stdout
			result.recordScanner("bandit", this.banditTool.version());
			processBanditOutput(toolResult.stdout(), result, toolResult.exitCode());
		}
		else {
			String errorMsg = toolResult.stderr().isEmpty() ? toolResult.stdout() : toolResult.stderr();
			result.addWarning("Bandit error (exit code " + toolResult.exitCode() + "): " + truncate(errorMsg, 200));
			result.markScanIncomplete("bandit");
		}

		return result;
	}

	/**
	 * Parse Bandit JSON output and report issues.
	 * @param output the Bandit JSON stdout
	 * @param result result the parsed findings and scan-incomplete flags are added to
	 * @param exitCode the Bandit process exit code, checked against the findings for
	 * consistency
	 */
	void processBanditOutput(String output, ValidationResult result, Integer exitCode) {
		JsonNode data = parseJsonOutput(output);
		if (data == null || !data.isObject()) {
			result.addError("Bandit did not return valid JSON; scan did not complete");
			result.markScanIncomplete("bandit");
			return;
		}

		JsonNode issues = data.get("results");
		boolean issuesValid = issues != null && issues.isArray();
		if (issuesValid) {
			for (JsonNode issue : issues) {
				issuesValid = issue.isObject();
				if (!issuesValid) {
					break;
				}
			}
		}
		if (!issuesValid) {
			result.addError("Bandit JSON report is missing a valid 'results' list; scan did not complete");
			result.markScanIncomplete("bandit");
			return;
		}

		JsonNode errors = data.get("errors");
		if (errors == null) {
			errors = MissingNode.getInstance();
		}
		if (!errors.isArray()) {
			if (!errors.isMissingNode()) {
				result.addError("Bandit JSON field 'errors' must be a list; scan did not complete");
				result.markScanIncomplete("bandit");
				return;
			}
			errors = MAPPER.createArrayNode();
		}
		if (errors.size() > 0) {
			int shown = 0;
			for (JsonNode error : errors) {
				if (shown++ == 3) {
					break;
				}
				String detail = pyStr(pyOr(error.get("reason"), error.get("message"), error));
				result.addError("Bandit scan error: " + truncate(pyTruthy(detail) ? detail : "Unknown error", 200));
			}
			result.markScanIncomplete("bandit");
		}

		if (exitCode != null && exitCode == 1 && issues.size() == 0) {
			result.addError("Bandit findings exit code 1 did not include findings; scan did not complete");
			result.markScanIncomplete("bandit");
			return;
		}
		if (exitCode != null && exitCode == 0 && issues.size() > 0) {
			result.addError("Bandit returned findings with clean exit code 0; scanner status is inconsistent");
			result.markScanIncomplete("bandit");
		}

		if (issues.size() == 0) {
			if (errors.size() == 0) {
				result.addSuccess("bandit", "Bandit: No security issues found");
			}
			return;
		}

		// Summarize by severity
		Map<String, Integer> counts = countBySeverity(issues);
		result.addMessage("Bandit found: " + counts.getOrDefault("HIGH", 0) + " HIGH, "
				+ counts.getOrDefault("MEDIUM", 0) + " MEDIUM, " + counts.getOrDefault("LOW", 0) + " LOW");

		for (JsonNode issue : issues) {
			reportBanditIssue(result, issue);
		}
	}

	/**
	 * Count issues by severity level (Python {@code _count_by_severity}).
	 * @param issues the Bandit ``results`` array
	 * @return the map seeded with ``HIGH``, ``MEDIUM`` and ``LOW``, each holding its
	 * finding count
	 */
	private Map<String, Integer> countBySeverity(JsonNode issues) {
		Map<String, Integer> counts = new LinkedHashMap<>();
		counts.put("HIGH", 0);
		counts.put("MEDIUM", 0);
		counts.put("LOW", 0);
		for (JsonNode issue : issues) {
			String sev = textOr(issue.get("issue_severity"), "LOW").toUpperCase(Locale.ROOT);
			counts.merge(sev, 1, Integer::sum);
		}
		return counts;
	}

	/**
	 * Report a single Bandit finding.
	 * @param result result the finding is added to
	 * @param issue one entry from Bandit's ``results`` array
	 */
	private void reportBanditIssue(ValidationResult result, JsonNode issue) {
		String severityStr = textOr(issue.get("issue_severity"), "MEDIUM").toUpperCase(Locale.ROOT);
		String confidence = textOr(issue.get("issue_confidence"), "MEDIUM").toUpperCase(Locale.ROOT);
		Severity severity = mapBanditSeverity(severityStr);

		String testId = textOr(issue.get("test_id"), "B000");
		String testName = textOr(issue.get("test_name"), "unknown");
		String issueText = textOr(issue.get("issue_text"), "Security issue detected");
		String filename = textOr(issue.get("filename"), "unknown");
		int line = (issue.get("line_number") != null && issue.get("line_number").isNumber())
				? issue.get("line_number").intValue() : 0;
		String cweId = textOr(issue.path("issue_cwe").path("id"), "");

		String cweRef = cweId.isEmpty() ? "" : " (CWE-" + cweId + ")";
		String message = issueText + cweRef;

		// HIGH severity or MEDIUM with decent confidence are errors
		boolean isError = "HIGH".equals(severityStr)
				|| ("MEDIUM".equals(severityStr) && ("HIGH".equals(confidence) || "MEDIUM".equals(confidence)));

		Map<String, Object> metadata = new LinkedHashMap<>();
		if (!cweId.isEmpty()) {
			metadata.put("cwe", cweId);
		}
		result.addStructuredFinding(new Finding("BANDIT", severity, testId + ":" + testName, message, filename, line,
				null, "Review " + testName + " (" + testId + ")" + cweRef, metadata), isError);
	}

	/**
	 * Map Bandit severity string to Severity enum.
	 * @param severityStr the Bandit ``issue_severity`` value, in any case
	 * @return the matching ``Severity``, defaulting to ``MEDIUM`` for an unrecognised
	 * value
	 */
	private Severity mapBanditSeverity(String severityStr) {
		return switch (severityStr.toUpperCase(Locale.ROOT)) {
			case "HIGH" -> Severity.HIGH;
			case "MEDIUM" -> Severity.MEDIUM;
			case "LOW" -> Severity.LOW;
			default -> Severity.MEDIUM;
		};
	}

	// ----------------------------------------------------------------- semgrep

	ValidationResult runSemgrep(Path skillPath) {
		ValidationResult result = new ValidationResult();

		if (!this.semgrepTool.isAvailable()) {
			if (requireSemgrep(System.getenv(REQUIRE_SEMGREP_ENV))) {
				result.addWarning("semgrep not installed. " + semgrepInstallHint());
				result.markScanIncomplete("semgrep");
			}
			else {
				result.addMessage("Semgrep not installed; skipped. " + semgrepInstallHint());
			}
			return result;
		}

		Path rulesPath = resolveSemgrepRulesPath();
		List<String> args = new ArrayList<>(List.of("scan", "--config", rulesPath.toString(), "--json", "--quiet",
				"--metrics", "off", "--disable-version-check", "--no-rewrite-rule-ids", "--error"));
		if (this.excludeTests) {
			args.addAll(List.of("--exclude", "tests", "--exclude", "test"));
		}
		for (String d : new TreeSet<>(Constants.SCAN_EXCLUDED_DIRS)) {
			args.addAll(List.of("--exclude", d));
		}
		for (String f : semgrepFileExcludes(skillPath)) {
			args.addAll(List.of("--exclude", f));
		}
		args.add(skillPath.toAbsolutePath().normalize().toString());

		ScannerTool.RunResult toolResult = this.semgrepTool.run(args);

		if (toolResult.errorMessage() != null) {
			result.addError(toolResult.errorMessage());
			result.markScanIncomplete("semgrep");
		}
		else if (toolResult.exitCode() == 0 || toolResult.exitCode() == 1) {
			// Exit code 0 = clean, 1 = findings; both emit JSON on stdout
			result.recordScanner("semgrep", this.semgrepTool.version());
			processSemgrepOutput(toolResult.stdout(), result, toolResult.exitCode(),
					skillPath.toAbsolutePath().normalize());
		}
		else {
			String errorMsg = toolResult.stderr().isEmpty()
					? (toolResult.stdout().isEmpty() ? "no diagnostic output" : toolResult.stdout())
					: toolResult.stderr();
			result.addError("Semgrep error (exit code " + toolResult.exitCode() + "): " + truncate(errorMsg, 200));
			result.markScanIncomplete("semgrep");
		}

		return result;
	}

	/**
	 * Whether the require-semgrep environment flag is on (Python truthiness: 1/true/yes,
	 * case-insensitive).
	 * @param rawValue raw value of the require-semgrep environment variable
	 * @return ``true`` when the stripped value is ``1``, ``true`` or ``yes``, ignoring
	 * case
	 */
	static boolean requireSemgrep(String rawValue) {
		String value = (rawValue != null) ? rawValue.strip() : "";
		return Set.of("1", "true", "yes").contains(value.toLowerCase(Locale.ROOT));
	}

	/**
	 * Parse Semgrep JSON output and report findings.
	 *
	 * <p>
	 * {@code skillPath} is the resolved skill root; it lets parse-error paths be shown
	 * relative to it.
	 * @param output the Semgrep JSON stdout
	 * @param result result the parsed findings are added to
	 */
	void processSemgrepOutput(String output, ValidationResult result) {
		processSemgrepOutput(output, result, null, null);
	}

	void processSemgrepOutput(String output, ValidationResult result, Integer exitCode, Path skillPath) {
		JsonNode data = parseJsonOutput(output);
		if (data == null) {
			result.addError("Semgrep did not return valid JSON; scan did not complete");
			result.markScanIncomplete("semgrep");
			return;
		}
		if (!data.isObject()) {
			result.addError("Semgrep JSON output was not an object; scan did not complete");
			result.markScanIncomplete("semgrep");
			return;
		}

		if (!data.has("results")) {
			result.addError("Semgrep JSON report is missing required 'results' list; scan did not complete");
			result.markScanIncomplete("semgrep");
			return;
		}
		JsonNode findings = data.get("results");
		if (!findings.isArray()) {
			result.addError("Semgrep JSON field 'results' must be a list; scan did not complete");
			result.markScanIncomplete("semgrep");
			return;
		}
		for (JsonNode finding : findings) {
			if (!finding.isObject()) {
				result.addError("Semgrep JSON 'results' entries must be objects; scan did not complete");
				result.markScanIncomplete("semgrep");
				return;
			}
		}

		if (!data.has("errors")) {
			result.addError("Semgrep JSON report is missing required 'errors' list; scan did not complete");
			result.markScanIncomplete("semgrep");
			return;
		}
		JsonNode scanErrors = data.get("errors");
		if (!scanErrors.isArray()) {
			result.addError("Semgrep JSON field 'errors' must be a list; scan did not complete");
			result.markScanIncomplete("semgrep");
			return;
		}
		for (JsonNode error : scanErrors) {
			if (!error.isObject()) {
				result.addError("Semgrep JSON 'errors' entries must be objects; scan did not complete");
				result.markScanIncomplete("semgrep");
				return;
			}
		}

		// File-level parse errors mean Semgrep ran but skipped part of the input; every
		// other error is reported one by one (limited to avoid noise).
		List<JsonNode> parseErrors = new ArrayList<>();
		List<JsonNode> otherErrors = new ArrayList<>();
		for (JsonNode error : scanErrors) {
			(isSemgrepParseError(error) ? parseErrors : otherErrors).add(error);
		}
		if (!parseErrors.isEmpty()) {
			reportSemgrepParseErrors(result, parseErrors, skillPath);
		}
		int shown = 0;
		for (JsonNode error : otherErrors) {
			if (shown++ == 3) {
				break;
			}
			String message = pyStr(pyOr(error.get("message"), error.get("type")));
			if (!pyTruthy(message)) {
				message = "Unknown scan error";
			}
			result.addError("Semgrep scan error: " + truncate(message, 100));
		}
		if (scanErrors.size() > 0) {
			result.markScanIncomplete("semgrep");
		}

		if (exitCode != null && exitCode == 1 && findings.size() == 0) {
			result.addError("Semgrep findings exit code 1 did not include findings; scan did not complete");
			result.markScanIncomplete("semgrep");
		}
		else if (exitCode != null && exitCode == 0 && findings.size() > 0) {
			result.addError("Semgrep returned findings with clean exit code 0; scanner status is inconsistent");
			result.markScanIncomplete("semgrep");
		}

		if (findings.size() == 0) {
			if (scanErrors.size() == 0 && !result.isIncomplete()) {
				result.addSuccess("semgrep", "Semgrep: No security issues found");
			}
			return;
		}

		result.addMessage("Semgrep found " + findings.size() + " issue(s)");

		for (JsonNode finding : findings) {
			reportSemgrepFinding(result, finding);
		}
	}

	/**
	 * Report files Semgrep could not fully parse as a partial scan.
	 *
	 * <p>
	 * The message names up to {@code _SEMGREP_PARSE_ERROR_PATH_LIMIT} files with the line
	 * of their first error; {@code semgrep_unparsed_files} keeps every file path,
	 * relative to the skill.
	 * @param result result the error message and metadata are added to
	 * @param parseErrors the file-level parse-error entries from Semgrep's ``errors``
	 * list
	 * @param skillPath skill root the reported paths are made relative to
	 */
	void reportSemgrepParseErrors(ValidationResult result, List<JsonNode> parseErrors, Path skillPath) {
		Map<String, Integer> firstLine = new LinkedHashMap<>();
		for (JsonNode error : parseErrors) {
			String path = semgrepDisplayPath(pyStr(pyOr(error.get("path"), DEFAULT_UNKNOWN_PATH)), skillPath);
			Integer line = semgrepErrorLine(error);
			if (firstLine.get(path) == null) {
				firstLine.put(path, line);
			}
		}
		List<String> files = new ArrayList<>(firstLine.keySet());
		List<String> locations = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : firstLine.entrySet()) {
			Integer line = entry.getValue();
			locations.add((line != null && line != 0) ? entry.getKey() + ":" + line : entry.getKey());
		}
		List<String> shown = locations.subList(0, Math.min(locations.size(), SEMGREP_PARSE_ERROR_PATH_LIMIT));
		int extra = locations.size() - shown.size();
		String listing = String.join(", ", shown) + ((extra > 0) ? ", and " + extra + " more" : "");
		result.getMetadata().put("semgrep_unparsed_files", files);
		result.addError("Semgrep partial scan: " + files.size() + " file(s) could not be fully parsed (" + listing
				+ "). Semgrep skips the code it cannot parse, so findings there are missed. Findings for the"
				+ " other files are kept. Fix the syntax error, or check whether it comes from Semgrep's parser"
				+ " or the rule configuration for that language.");
	}

	/**
	 * Report a single Semgrep finding.
	 * @param result result the finding is added to
	 * @param finding one entry from Semgrep's ``results`` list
	 */
	private void reportSemgrepFinding(ValidationResult result, JsonNode finding) {
		String ruleId = textOr(finding.get("check_id"), "unknown");
		JsonNode extra = finding.get("extra");
		if (extra == null || !extra.isObject()) {
			extra = MissingNode.getInstance();
		}

		String severityStr = textOr(extra.get("severity"), "WARNING").toUpperCase(Locale.ROOT);
		Severity severity = "ERROR".equals(severityStr) ? Severity.HIGH : Severity.MEDIUM;

		String message = truncate(textOr(extra.get("message"), "Issue detected"), 100);
		String path = textOr(finding.get("path"), "unknown");
		int line = finding.path("start").path("line").isNumber() ? finding.path("start").path("line").intValue() : 0;

		// Build reference string for CWE/OWASP
		JsonNode metadata = extra.get("metadata");
		if (metadata == null || !metadata.isObject()) {
			metadata = MissingNode.getInstance();
		}
		String refs = buildReferenceString(metadata);

		result.addStructuredFinding(
				new Finding("SEMGREP", severity, ruleId, message, path, line, null,
						refs.isEmpty() ? null : stripSpacesAndParens(refs), toMap(metadata)),
				severity == Severity.HIGH);
	}

	/**
	 * Build CWE/OWASP reference string from metadata.
	 * @param metadata the Semgrep finding's ``extra.metadata`` object
	 * @return the parenthesised CWE/OWASP reference string, empty when the metadata names
	 * neither
	 */
	String buildReferenceString(JsonNode metadata) {
		List<String> refs = new ArrayList<>();
		List<String> cwe = stringList(metadata.get("cwe"));
		if (!cwe.isEmpty()) {
			refs.add("CWE: " + String.join(", ", cwe.subList(0, Math.min(cwe.size(), 2))));
		}
		List<String> owasp = stringList(metadata.get("owasp"));
		if (!owasp.isEmpty()) {
			refs.add("OWASP: " + String.join(", ", owasp.subList(0, Math.min(owasp.size(), 2))));
		}
		return refs.isEmpty() ? "" : " (" + String.join("; ", refs) + ")";
	}

	// ------------------------------------------------------- semgrep helpers

	/**
	 * Return the Semgrep error type name; JSON reports use a string or
	 * {@code ["Name", ...]}.
	 * @param error one entry from Semgrep's ``errors`` list
	 * @return the error type name, empty when the record has none
	 */
	static String semgrepErrorType(JsonNode error) {
		JsonNode errorType = error.get("type");
		if (errorType != null && errorType.isArray() && errorType.size() > 0) {
			errorType = errorType.get(0);
		}
		return (errorType != null && errorType.isTextual()) ? errorType.textValue() : "";
	}

	/**
	 * True for a file-level parse error: Semgrep ran but could not parse one input file.
	 * @param error one entry from Semgrep's ``errors`` list
	 * @return ``true`` when it names a path and its type is a known parse-error type
	 */
	static boolean isSemgrepParseError(JsonNode error) {
		JsonNode path = error.get("path");
		return pyTruthy(path) && SEMGREP_PARSE_ERROR_TYPES.contains(semgrepErrorType(error));
	}

	/**
	 * Return {@code path} relative to the scanned skill.
	 *
	 * <p>
	 * Semgrep reports absolute paths for the absolute target {@code runSemgrep} passes,
	 * so a raw path would put the user's home directory into every report. A path outside
	 * the skill keeps only its file name.
	 * @param path the path Semgrep reported, absolute or relative
	 * @param skillRoot the scanned skill root, or ``null`` when no root is known
	 * @return the path relative to ``skillRoot``, or its file name when it lies outside
	 * the skill
	 */
	static String semgrepDisplayPath(String path, Path skillRoot) {
		Path candidate = Path.of(path);
		if (!candidate.isAbsolute()) {
			return path.replace('\\', '/');
		}
		if (skillRoot != null) {
			Path root = skillRoot.toAbsolutePath().normalize();
			Path absolute = candidate.toAbsolutePath().normalize();
			// Python relative_to() refuses anything that is not strictly beneath the
			// root; Java relativize happily produces "../" paths, so gate on startsWith.
			if (absolute.startsWith(root)) {
				return root.relativize(absolute).toString().replace('\\', '/');
			}
		}
		return candidate.getFileName().toString();
	}

	/**
	 * First span start line of a Semgrep error record, or null.
	 * @param error one entry from Semgrep's ``errors`` list
	 * @return the first span's start line, or ``null`` when the record has no numeric one
	 */
	static Integer semgrepErrorLine(JsonNode error) {
		JsonNode spans = error.get("spans");
		if (spans != null && spans.isArray() && spans.size() > 0 && spans.get(0).isObject()) {
			JsonNode start = spans.get(0).get("start");
			if (start != null && start.isObject() && start.get("line") != null && start.get("line").isNumber()) {
				return start.get("line").intValue();
			}
		}
		return null;
	}

	/**
	 * Return generated file patterns using the target filesystem's case semantics.
	 *
	 * <p>
	 * Mirrors Python {@code matches_filesystem_name}: an exact-spelling entry always
	 * counts; a differently-cased entry counts only when it is the same physical inode as
	 * the canonical spelling, so an authored {@code benchmark.md} on a case-sensitive
	 * filesystem is not mistaken for the generated {@code BENCHMARK.md}.
	 * @param skillPath the skill directory being scanned
	 * @return the canonical excluded-file names plus this skill's differently-cased
	 * aliases
	 */
	static List<String> semgrepFileExcludes(Path skillPath) {
		Set<String> excludes = new TreeSet<>(Constants.SCAN_EXCLUDED_FILES);
		try (var entries = Files.list(skillPath)) {
			for (Path entry : entries.toList()) {
				if (Files.isRegularFile(entry) && matchesGeneratedName(entry, Constants.SCAN_EXCLUDED_FILES)) {
					excludes.add(entry.getFileName().toString());
				}
			}
		}
		catch (IOException ignored) {
			// OSError in Python: keep the canonical set.
		}
		return new ArrayList<>(excludes);
	}

	/**
	 * Filesystem-case-aware name match; see {@link #semgrepFileExcludes}.
	 * @param entry the file whose name is being matched
	 * @param canonicalNames the generated-file names to match against
	 * @return ``true`` for an exact name, or a case-insensitive one proven to be the same
	 * file
	 */
	private static boolean matchesGeneratedName(Path entry, Set<String> canonicalNames) {
		String name = entry.getFileName().toString();
		if (canonicalNames.contains(name)) {
			return true;
		}
		for (String canonical : canonicalNames) {
			if (!canonical.equalsIgnoreCase(name)) {
				continue;
			}
			try {
				Object observedKey = Files.getAttribute(entry, "basic:fileKey");
				Object canonicalKey = Files.getAttribute(entry.resolveSibling(canonical), "basic:fileKey");
				if (observedKey != null && observedKey.equals(canonicalKey)) {
					return true;
				}
			}
			catch (IOException | UnsupportedOperationException | SecurityException ignored) {
				// No alias without proof of the same physical file (Python returns False
				// here).
			}
		}
		return false;
	}

	// --------------------------------------------------- semgrep rules config

	/**
	 * Resolve the packaged Semgrep rules file: env override, then the classpath resource
	 * (used in place when it is a {@code file:} resource, else copied to a temp file),
	 * then a conventional operator-supplied fallback location.
	 * @return the resolved rules file, cached after the first call
	 */
	private Path resolveSemgrepRulesPath() {
		if (this.semgrepRulesPath == null) {
			String env = System.getenv(SEMGREP_RULES_ENV);
			if (env != null && !env.isBlank()) {
				this.semgrepRulesPath = Path.of(env.strip()).toAbsolutePath().normalize();
			}
			else {
				this.semgrepRulesPath = extractSemgrepRulesResource();
			}
		}
		return this.semgrepRulesPath;
	}

	private static Path extractSemgrepRulesResource() {
		ClassLoader loader = CodeRiskValidator.class.getClassLoader();
		URL url = (loader != null) ? loader.getResource(SEMGREP_RULES_RESOURCE) : null;
		if (url == null) {
			return Path.of(System.getProperty("user.home"), ".skillevaluator", "config", "semgrep_rules.yaml")
				.toAbsolutePath()
				.normalize();
		}
		try {
			URI uri = url.toURI();
			if ("file".equals(uri.getScheme())) {
				return Path.of(uri);
			}
		}
		catch (URISyntaxException | IllegalArgumentException ignored) {
			// fall through to the temp-copy path
		}
		try (InputStream in = url.openStream()) {
			Path temp = Files.createTempFile("skillevaluator_semgrep_rules", ".yaml");
			Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
			return temp;
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	// -------------------------------------------------------- install hints

	static String banditInstallHint() {
		return "Bundled with SkillEvaluator — reinstall with: "
				+ "uv tool install --reinstall \"skillevaluator @ git+https://github.com/NVIDIA/SkillEvaluator.git\"";
	}

	static String semgrepInstallHint() {
		return "Optional; install separately with: brew install semgrep (macOS) or uv tool install semgrep";
	}

	// ------------------------------------------------------------ json/jvm helpers

	/**
	 * Port of {@code parse_json_output}: parsed JSON or null when it cannot be parsed.
	 * @param output the raw stdout to parse
	 * @return the parsed tree, or ``null`` when the text is blank or not valid JSON
	 */
	static JsonNode parseJsonOutput(String output) {
		if (output == null || output.isBlank()) {
			return null;
		}
		try {
			return MAPPER.readTree(output);
		}
		catch (IOException ex) {
			return null;
		}
	}

	/**
	 * Python {@code str(node)} for JSON scalars/containers ({@code null} renders
	 * {@code "None"}, booleans {@code True}/{@code False}).
	 * @param node the JSON node to stringify
	 * @return the Python-style string of the node
	 */
	static String pyStr(JsonNode node) {
		if (node == null || node.isMissingNode()) {
			return "None";
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

	/**
	 * First of the candidate nodes that is Python-truthy; the last candidate is the
	 * fallback.
	 * @param candidates the nodes to test in order, the last one the fallback
	 * @return the first Python-truthy candidate, or the last when none is
	 */
	static JsonNode pyOr(JsonNode... candidates) {
		for (int i = 0; i < candidates.length - 1; i++) {
			if (pyTruthy(candidates[i])) {
				return candidates[i];
			}
		}
		return candidates[candidates.length - 1];
	}

	/**
	 * Python truthiness for JSON values (null/false/0/""/[]/{} are falsy).
	 * @param node the JSON node to test
	 * @return ``true`` unless the node is missing, null, ``false``, zero, empty or an
	 * empty container
	 */
	static boolean pyTruthy(JsonNode node) {
		if (node == null || node.isMissingNode() || node.isNull()) {
			return false;
		}
		if (node.isBoolean()) {
			return node.booleanValue();
		}
		if (node.isNumber()) {
			return node.doubleValue() != 0.0;
		}
		if (node.isTextual()) {
			return !node.textValue().isEmpty();
		}
		if (node.isContainerNode()) {
			return node.size() > 0;
		}
		return true;
	}

	/**
	 * Python truthiness for an already-extracted string.
	 * @param text the string to test
	 * @return ``true`` when it is neither null nor empty
	 */
	static boolean pyTruthy(String text) {
		return text != null && !text.isEmpty();
	}

	/**
	 * Python {@code dict.get(key, default)}: the default applies only to a MISSING key; a
	 * present {@code null} converts to "None" exactly as Python's {@code str(None)} would
	 * downstream.
	 * @param node the node to read
	 * @param fallback value returned when the key is missing
	 * @return ``pyStr(node)``, or ``fallback`` when the node is missing
	 */
	static String textOr(JsonNode node, String fallback) {
		if (node == null || node.isMissingNode()) {
			return fallback;
		}
		return pyStr(node);
	}

	/**
	 * Python {@code list} of the string entries of a JSON array; empty for anything else.
	 * @param node the node to read
	 * @return the stringified entries of a JSON array, empty for anything else
	 */
	static List<String> stringList(JsonNode node) {
		List<String> out = new ArrayList<>();
		if (node != null && node.isArray()) {
			for (JsonNode item : node) {
				out.add(item.isTextual() ? item.textValue() : pyStr(item));
			}
		}
		return out;
	}

	/**
	 * JSON object to an insertion-ordered map; empty map for anything else.
	 * @param node the node to read
	 * @return the object's properties as a map, empty for anything else
	 */
	static Map<String, Object> toMap(JsonNode node) {
		Map<String, Object> out = new LinkedHashMap<>();
		if (node != null && node.isObject()) {
			node.properties().forEach((entry) -> out.put(entry.getKey(), toJavaValue(entry.getValue())));
		}
		return out;
	}

	private static Object toJavaValue(JsonNode node) {
		if (node.isObject()) {
			return toMap(node);
		}
		if (node.isArray()) {
			List<Object> list = new ArrayList<>();
			node.forEach((item) -> list.add(toJavaValue(item)));
			return list;
		}
		if (node.isTextual()) {
			return node.textValue();
		}
		if (node.isBoolean()) {
			return node.booleanValue();
		}
		if (node.isNumber()) {
			return node.numberValue();
		}
		return null;
	}

	/**
	 * Python-style code-point truncation ({@code s[:limit]}).
	 * @param text the text to shorten
	 * @param limit the maximum number of code points to keep
	 * @return the text truncated to ``limit`` code points, empty for null
	 */
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
	 * Python's {@code str.strip(" ()")}: trim spaces and parentheses from both ends.
	 * @param text the text to trim
	 * @return the text with leading and trailing spaces and parentheses removed
	 */
	static String stripSpacesAndParens(String text) {
		int start = 0;
		int end = text.length();
		while (start < end && (text.charAt(start) == ' ' || text.charAt(start) == '(')) {
			start++;
		}
		while (end > start && (text.charAt(end - 1) == ' ' || text.charAt(end - 1) == ')')) {
			end--;
		}
		return text.substring(start, end);
	}

	private static int asInt(Object value) {
		return (value instanceof Number n) ? n.intValue() : 0;
	}

	/**
	 * Seam for injecting a fake scanner CLI in tests; the default implementation shells
	 * out.
	 */
	public interface ScannerTool {

		/**
		 * Whether the backing executable can be resolved at all.
		 * @return ``true`` when the executable resolves and no configuration error was
		 * recorded
		 */
		boolean isAvailable();

		/**
		 * Version string reported by the tool, or null.
		 * @return the tool's version, or ``null`` when it is unavailable or reports none
		 */
		String version();

		/**
		 * Invoke the tool with these arguments.
		 * @param args the arguments to pass the executable
		 * @return the outcome of the invocation
		 */
		RunResult run(List<String> args);

		/**
		 * Outcome of one invocation; mirrors the Python {@code ToolResult}.
		 *
		 * @param success whether the process ran to completion
		 * @param stdout captured standard output
		 * @param stderr captured standard error
		 * @param exitCode the process exit code
		 * @param errorMessage error to surface when the tool was unavailable or
		 * misconfigured
		 * @author Ivan Rodriguez
		 */
		record RunResult(boolean success, String stdout, String stderr, int exitCode, String errorMessage) {
		}

	}

	// ----------------------------------------------------------- CLI tool impl

	/**
	 * Default {@link ScannerTool}: resolves the executable like Python's
	 * {@code ExternalTool} (validated env override, then PATH) and shells out through
	 * {@link ToolRunner} with Python-compatible error messages.
	 */
	static final class CliTool implements ScannerTool {

		private final String name;

		private final String command;

		private final String overrideEnv;

		private final String installHint;

		/** Resolved executable, or null when unavailable. */
		private final String path;

		/** Why executable selection failed; surfaces as the install hint. */
		private final String configurationError;

		private String cachedVersion;

		CliTool(String name, String command, String overrideEnv, String installHint) {
			this.name = name;
			this.command = command;
			this.overrideEnv = overrideEnv;
			this.installHint = installHint;
			String resolvedPath = null;
			String configError = null;
			String override = envOrEmpty(overrideEnv).strip();
			if (!override.isEmpty()) {
				Path candidate = Path.of(override);
				if (!candidate.isAbsolute() || !Files.isRegularFile(candidate) || !Files.isExecutable(candidate)) {
					configError = overrideEnv + " must name an absolute executable file; got '" + override + "'";
				}
				else {
					resolvedPath = candidate.toAbsolutePath().normalize().toString();
				}
			}
			else {
				resolvedPath = firstOnPath(command);
			}
			this.path = resolvedPath;
			this.configurationError = configError;
		}

		@Override
		public boolean isAvailable() {
			return this.path != null && this.configurationError == null;
		}

		@Override
		public String version() {
			if (this.path == null) {
				return null;
			}
			if (this.cachedVersion == null) {
				ToolRunner.ToolResult probe = ToolRunner.run(List.of(this.path, "--version"), null, 30);
				String haystack = probe.stdout() + "\n" + probe.stderr();
				var matcher = VERSION_PATTERN.matcher(haystack);
				this.cachedVersion = matcher.find() ? matcher.group() : "";
			}
			return this.cachedVersion.isEmpty() ? null : this.cachedVersion;
		}

		@Override
		public RunResult run(List<String> args) {
			if (!isAvailable()) {
				return new RunResult(false, "", "", -1, this.name + " not installed. "
						+ ((this.configurationError != null) ? this.configurationError : this.installHint));
			}
			List<String> cmd = new ArrayList<>();
			cmd.add(this.path);
			cmd.addAll(args);
			ToolRunner.ToolResult raw = ToolRunner.run(cmd, null, defaultTimeoutSeconds());
			if (raw.exitCode() == -1) {
				return new RunResult(false, raw.stdout(), raw.stderr(), -1,
						this.name + " timed out after " + defaultTimeoutSeconds() + " seconds");
			}
			if (raw.exitCode() == 127) {
				return new RunResult(false, "", "", -1, this.name + " binary not found in PATH");
			}
			if (raw.exitCode() == 124) {
				return new RunResult(false, raw.stdout(), raw.stderr(), -1,
						this.name + " execution failed: interrupted");
			}
			return new RunResult(true, raw.stdout(), raw.stderr(), raw.exitCode(), null);
		}

		/**
		 * Python's per-scanner timeouts: bandit 120s, semgrep 180s.
		 * @return ``180`` for Semgrep, ``120`` for every other scanner
		 */
		private long defaultTimeoutSeconds() {
			return "semgrep".equals(this.command) ? 180 : 120;
		}

		private String firstOnPath(String executable) {
			return ToolRunner.isAvailable(executable) ? executable : null;
		}

		private static String envOrEmpty(String envVar) {
			String value = System.getenv(envVar);
			return (value != null) ? value : "";
		}

	}

}
