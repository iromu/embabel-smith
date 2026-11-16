/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.spi;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import org.iromu.embabel.smith.skillevaluator.model.Finding;
import org.iromu.embabel.smith.skillevaluator.model.Severity;
import org.iromu.embabel.smith.skillevaluator.model.ValidationResult;
import org.iromu.embabel.smith.skillevaluator.util.ToolRunner;

/**
 * The built-in {@link SecurityScanner}: run the SkillSpector CLI and turn its JSON report
 * into findings.
 * <p>
 * Command: {@code skillspector scan <absolute path> --format json --no-llm}, the
 * deterministic stage upstream always runs first. {@code --no-llm} is unconditional here
 * because this port has no LLM stage at all; upstream's second LLM-enrichment pass, its
 * {@code --baseline} suppression file, and its artifact-dir staging (copying the skill to
 * a temp dir that drops {@code evals/}/{@code results/} snapshots before the scan) are
 * not implemented. The watchdog is the same 1200 seconds upstream defaults to,
 * overridable through {@code SKILLEVALUATOR_SKILLSPECTOR_TIMEOUT_SECONDS}. Exit codes 0
 * and 1 are the scanner's policy codes (clean and findings); anything else fails the
 * stage.
 * <h2>This engine is deliberately minimal — and replaceable</h2>
 * <p>
 * Upstream does a great deal between "skillspector printed JSON" and "findings exist":
 * generated-artifact/SPDX-comment/passwd-mount issue filters, content-type control-path
 * scoping, an {@code AE1} referenced-artifact diagnostic index, report path normalization
 * and redaction, finding-identity reconciliation against the report's own risk score,
 * unverified-hook gate failures, and baseline suppression accounting. <b>None of that is
 * ported.</b> What is kept is the contract every consumer of this check actually reads:
 * each {@code issues[]} entry becomes one finding with the same category, severity, check
 * name, message, location and metadata keys as upstream's
 * {@code _convert_skillspector_issue}; the aggregate risk-score gate stays; and a scan
 * that cannot be trusted says so through {@code addError} plus
 * {@code markScanIncomplete}.
 * <p>
 * That gap is why the scanner sits behind an SPI rather than inside the validator: an
 * integrator who needs the full triage layer — or a different scanner entirely —
 * implements {@link SecurityScanner} and registers it (ServiceLoader, or
 * {@code SecurityValidator(SecurityScanner)}) instead of patching this class. Treat the
 * class below as the reference engine, not as parity.
 * <h2>Documented deviations</h2>
 * <ul>
 * <li><b>Scanner version source.</b> Upstream records the version of the installed binary
 * (probed once at tool detection). Here {@code recordScanner} gets the report's
 * {@code metadata.skillspector_version} when the scanner emits one, and nothing otherwise
 * — no extra {@code --version} subprocess per scan.</li>
 * <li><b>Redacted diagnostics.</b> A non-policy exit code reports the code and says the
 * diagnostics were redacted, as upstream does. SkillSpector's stderr can quote the
 * scanned content, so it is not forwarded into the result.</li>
 * <li><b>Install hint.</b> Upstream's hint points at the wheel URL pinned by the
 * SkillEvaluator release this port follows; the URL names that version, so it goes stale
 * when the pin moves.</li>
 * <li><b>Paths.</b> Reported paths are whatever SkillSpector printed for the absolute
 * scan root. Upstream rewrites staged-copy prefixes back to the original skill, which
 * only matters with staging (not implemented).</li>
 * </ul>
 *
 * @author Ivan Rodriguez
 */
public final class SkillspectorSecurityScanner implements SecurityScanner {

	/** Tool name as upstream's tool registry knows it. */
	public static final String TOOL_NAME = "skillspector";

	/** Upstream {@code _DEFAULT_SKILLSPECTOR_TIMEOUT_SECONDS}. */
	public static final int DEFAULT_TIMEOUT_SECONDS = 1200;

	/** Upstream {@code _SKILLSPECTOR_TIMEOUT_ENV}. */
	public static final String TIMEOUT_ENV_VAR = "SKILLEVALUATOR_SKILLSPECTOR_TIMEOUT_SECONDS";

	/**
	 * Upstream {@code _SKILLSPECTOR_POLICY_EXIT_CODES}: clean scan and findings-present
	 * scan.
	 */
	static final List<Integer> POLICY_EXIT_CODES = List.of(0, 1);

	/**
	 * Upstream {@code get_install_hint("skillspector")}: the wheel URL pinned by
	 * {@code utils/scanner_install.py} (SkillSpector 2.13.4 at the revision this port
	 * follows).
	 */
	static final String INSTALL_HINT = "Install with: uv tool install https://github.com/NVIDIA/SkillSpector/releases/download/"
			+ "v2.13.4/skillspector-2.13.4-py3-none-any.whl";

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Override
	public String getName() {
		return TOOL_NAME;
	}

	@Override
	public boolean isAvailable() {
		return ToolRunner.isAvailable(TOOL_NAME);
	}

	/**
	 * Port of {@code _run_skillspector_once} without the LLM stage, baseline or staging.
	 */
	@Override
	public ValidationResult scanSecurity(Path skillPath) {
		ValidationResult result = new ValidationResult();

		if (!isAvailable()) {
			result.addWarning("skillspector not installed - skipping security scan. " + INSTALL_HINT);
			result.markScanIncomplete(TOOL_NAME);
			return result;
		}

		Path scanRoot = skillPath.toAbsolutePath();
		List<String> command = List.of(TOOL_NAME, "scan", scanRoot.toString(), "--format", "json", "--no-llm");
		ToolRunner.ToolResult toolResult = ToolRunner.run(command, null, timeoutSeconds());

		if (!POLICY_EXIT_CODES.contains(toolResult.exitCode())) {
			result.addError("skillspector failed with unexpected exit code " + toolResult.exitCode()
					+ ": scanner diagnostics were redacted");
			result.markScanIncomplete(TOOL_NAME);
			return result;
		}

		JsonNode data = parseJson(toolResult.stdout());
		if (data == null) {
			result.addError("skillspector did not return valid JSON (exit code " + toolResult.exitCode()
					+ "); security scan did not complete");
			result.markScanIncomplete(TOOL_NAME);
			return result;
		}
		if (!data.isObject()) {
			result.addError("skillspector JSON output was not an object; security scan did not complete");
			result.markScanIncomplete(TOOL_NAME);
			return result;
		}
		result.recordScanner(TOOL_NAME, reportVersion(data));

		convertIssues(data, result);
		return result;
	}

	// ------------------------------------------------------------- conversion

	/**
	 * Python's {@code _process_skillspector_cli_result}, minus its filtering and scoring
	 * layers.
	 * @param data the parsed report object
	 * @param result the result each issue becomes a finding in, plus the risk-score gate
	 */
	private void convertIssues(JsonNode data, ValidationResult result) {
		int issueCount = 0;
		boolean hasCriticalOrHigh = false;
		for (JsonNode issue : iterable(data.get("issues"))) {
			if (!issue.isObject()) {
				continue;
			}
			issueCount++;
			Finding finding = toFinding(issue);
			boolean isError = finding.getSeverity() == Severity.CRITICAL || finding.getSeverity() == Severity.HIGH;
			hasCriticalOrHigh |= isError;
			result.addStructuredFinding(finding, isError);
		}

		Integer reportedScore = riskScore(data);
		if (reportedScore != null && reportedScore > 50) {
			String severity = text(object(data.get("risk_assessment")), "severity", "UNKNOWN");
			result.addMessage("skillspector risk score: " + reportedScore + "/100 (" + severity + ")");
		}
		if (reportedScore != null && reportedScore > 50 && !hasCriticalOrHigh) {
			// The aggregate score alone fails the gate, so a skill whose findings
			// are each individually advisory cannot slip through.
			result.addStructuredFinding(new Finding("SECURITY", Severity.HIGH, "skillspector_risk_score",
					"SkillSpector aggregate risk score " + reportedScore + "/100 exceeds the policy threshold",
					skillSource(data), null, null, "Review and resolve the contributing security findings", null),
					true);
			hasCriticalOrHigh = true;
		}

		summarize(issueCount, hasCriticalOrHigh, result);
	}

	/**
	 * Python {@code _convert_skillspector_issue}.
	 * @param issue one {@code issues[]} entry of the report
	 * @return its SECURITY finding, with the snippet truncated to 200 code points and the
	 * snippet and suggestion dropped when empty
	 */
	static Finding toFinding(JsonNode issue) {
		String severity = text(issue, "severity", "UNKNOWN").toUpperCase(Locale.ROOT);
		String explanation = text(issue, "explanation", "");
		String remediation = text(issue, "remediation", "");
		String suggestion = (explanation + " " + remediation).strip();
		String codeSnippet = text(issue, "code_snippet", "");
		String pattern = text(issue, "pattern", "Unknown");
		String category = text(issue, "category", "");
		String findingText = text(issue, "finding", "");

		JsonNode location = object(issue.get("location"));
		// Python returns ("unknown", None) for a missing or non-dict location; object()
		// already folds the non-dict case into a missing node, which reads as absent.
		String filePath = text(location, "file", "unknown");
		Integer lineNumber = firstInt(location, "start_line", "line");

		return new Finding("SECURITY", Severity.fromString(severity), pattern + " (" + text(issue, "id", "?") + ")",
				buildIssueMessage(category, findingText, explanation, pattern), filePath, lineNumber,
				codeSnippet.isEmpty() ? null : truncate(codeSnippet, 200), suggestion.isEmpty() ? null : suggestion,
				issueMetadata(issue));
	}

	/**
	 * Python {@code _build_issue_metadata}.
	 * @param issue one {@code issues[]} entry of the report
	 * @return the confidence when the report carries one, plus the intent when it is not
	 * empty
	 */
	static Map<String, Object> issueMetadata(JsonNode issue) {
		Map<String, Object> metadata = new LinkedHashMap<>();
		JsonNode confidence = issue.get("confidence");
		if (confidence != null && !confidence.isNull()) {
			metadata.put("skillspector_confidence", toJavaValue(confidence));
		}
		String intent = text(issue, "intent", "");
		if (!intent.isEmpty()) {
			metadata.put("intent", intent);
		}
		return metadata;
	}

	/**
	 * Python {@code _build_issue_message}.
	 * @param category the issue category, prefixed to the text when not empty
	 * @param findingText the issue's own finding, preferred over the explanation
	 * @param explanation the explanation, clipped to 120 code points and used only when
	 * {@code findingText} is empty
	 * @param patternName the pattern name, the answer when neither of the others is set
	 * @return the message to report for the issue
	 */
	static String buildIssueMessage(String category, String findingText, String explanation, String patternName) {
		if (!findingText.isEmpty()) {
			return category.isEmpty() ? findingText : category + ": " + findingText;
		}
		if (!explanation.isEmpty()) {
			String clipped = truncate(explanation, 120);
			return category.isEmpty() ? clipped : category + ": " + clipped;
		}
		return patternName;
	}

	/**
	 * Python {@code _summarize_skillspector_results}, without the baseline/applicability
	 * branches.
	 * @param issueCount the number of issues converted from the report
	 * @param hasCriticalOrHigh whether any of them was critical or high severity
	 * @param result the result the success or message entry is added to
	 */
	private static void summarize(int issueCount, boolean hasCriticalOrHigh, ValidationResult result) {
		if (issueCount == 0) {
			result.addSuccess(TOOL_NAME, "No security vulnerabilities detected (secrets, API keys, credentials)");
		}
		else if (hasCriticalOrHigh) {
			result.addMessage("skillspector found " + issueCount + " issue(s) including critical/high severity");
		}
		else {
			result.addSuccess(TOOL_NAME,
					"Security scan completed - " + issueCount + " advisory finding(s) (no critical/high issues)",
					Map.of("issue_count", issueCount));
		}
	}

	// -------------------------------------------------------------- plumbing

	/**
	 * Upstream's {@code _skillspector_timeout_seconds}: the operator override when it
	 * parses as a positive integer, else the 1200-second default.
	 * @return the watchdog, in seconds, for one CLI invocation
	 */
	static long timeoutSeconds() {
		String raw = System.getenv(TIMEOUT_ENV_VAR);
		if (raw != null) {
			try {
				long parsed = Long.parseLong(raw.strip());
				if (parsed > 0) {
					return parsed;
				}
			}
			catch (NumberFormatException ignored) {
				// Falls through to the default, as upstream's logger warning notes.
			}
		}
		return DEFAULT_TIMEOUT_SECONDS;
	}

	/**
	 * Python {@code parse_json_output}: null on anything but a well-formed JSON value.
	 * @param stdout the raw output of the CLI run
	 * @return the parsed tree, or {@code null} for blank or malformed output
	 */
	private static JsonNode parseJson(String stdout) {
		if (stdout == null || stdout.isBlank()) {
			return null;
		}
		try {
			return MAPPER.readTree(stdout);
		}
		catch (JsonProcessingException ex) {
			return null;
		}
	}

	private static String reportVersion(JsonNode data) {
		JsonNode version = object(data.get("metadata")).get("skillspector_version");
		return (version == null || version.isNull() || version.asText().isEmpty()) ? null : version.asText();
	}

	/**
	 * {@code data["risk_assessment"]["score"]} when the report carries an integer score.
	 * @param data the parsed report
	 * @return the aggregate score, or {@code null} when the report has no numeric one
	 */
	private static Integer riskScore(JsonNode data) {
		JsonNode assessment = object(data.get("risk_assessment"));
		JsonNode score = assessment.get("score");
		return (score != null && score.isNumber()) ? score.asInt() : null;
	}

	/**
	 * {@code (data.get("skill") or {}).get("source") or "SKILL.md"}.
	 * @param data the parsed report
	 * @return the scanned skill's source, or {@code SKILL.md} when the report omits it
	 */
	private static String skillSource(JsonNode data) {
		String source = text(object(data.get("skill")), "source", "");
		return source.isEmpty() ? "SKILL.md" : source;
	}

	/**
	 * Python's {@code issue.get(key) or default}: a falsy JSON value takes the fallback.
	 * @param node the object to read a field from
	 * @param key the field name
	 * @param fallback the value to use when the field is absent or falsy
	 * @return the field as text, or {@code fallback}
	 */
	private static String text(JsonNode node, String key, String fallback) {
		JsonNode value = node.get(key);
		return (value == null || falsy(value)) ? fallback : value.isTextual() ? value.asText() : value.toString();
	}

	/**
	 * Python truthiness over a JSON value: null, {@code false}, 0, {@code ""} and empty
	 * containers.
	 * @param value the JSON value to test
	 * @return {@code true} when Python would treat {@code value} as false
	 */
	private static boolean falsy(JsonNode value) {
		if (value.isNull() || value.isBoolean() && !value.asBoolean()) {
			return true;
		}
		if (value.isNumber()) {
			return value.asDouble() == 0.0;
		}
		return value.isTextual() ? value.textValue().isEmpty() : value.isContainerNode() && value.isEmpty();
	}

	/**
	 * {@code loc.get("start_line") or loc.get("line")}: first truthy integer-ish value.
	 * @param node the object to read, typically an issue location
	 * @param first the preferred field name
	 * @param second the field tried when {@code first} is absent or not a number
	 * @return the first non-zero numeric field, or {@code null} when neither is one
	 */
	private static Integer firstInt(JsonNode node, String first, String second) {
		for (String key : List.of(first, second)) {
			JsonNode value = node.get(key);
			if (value != null && value.isNumber() && value.asInt() != 0) {
				return value.asInt();
			}
		}
		return null;
	}

	private static Object toJavaValue(JsonNode node) {
		if (node.isBoolean()) {
			return node.asBoolean();
		}
		if (node.isNumber()) {
			return node.isIntegralNumber() ? (Object) node.asLong() : (Object) node.asDouble();
		}
		return node.asText();
	}

	/**
	 * Python's {@code x if isinstance(x, dict) else {}}.
	 * @param node the value to read as an object, possibly {@code null}
	 * @return {@code node} when it is a JSON object, else the missing node
	 */
	private static JsonNode object(JsonNode node) {
		return (node == null || !node.isObject()) ? MissingNode.getInstance() : node;
	}

	private static Iterable<JsonNode> iterable(JsonNode node) {
		return (node != null && node.isArray()) ? node : List.of();
	}

	/**
	 * Python's {@code value[:200]}: a code-point bound, not a UTF-16 one.
	 * @param value the text to shorten
	 * @param maxCodePoints the code points to keep
	 * @return {@code value}, cut at {@code maxCodePoints} when it is longer
	 */
	static String truncate(String value, int maxCodePoints) {
		int count = value.codePointCount(0, value.length());
		return (count <= maxCodePoints) ? value : value.substring(0, value.offsetByCodePoints(0, maxCodePoints));
	}

}
