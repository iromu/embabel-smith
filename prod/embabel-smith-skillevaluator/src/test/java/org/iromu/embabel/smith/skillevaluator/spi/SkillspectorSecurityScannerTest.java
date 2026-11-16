/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.iromu.embabel.smith.skillevaluator.model.Finding;
import org.iromu.embabel.smith.skillevaluator.model.Severity;

/**
 * Unit tests for the SkillSpector report-&gt;Finding conversion, ported from the
 * skillspector expectations in {@code tests/validators/test_security.py}
 * ({@code test_skillspector_policy_exit_one_processes_findings} and the
 * {@code _skillspector_json_report} fixtures that feed it).
 *
 * <p>
 * These live in the scanner's own package because the conversion mirrors Python private
 * methods ({@code _convert_skillspector_issue}, {@code _build_issue_message},
 * {@code _build_issue_metadata}) and stays package-private here; the validator-facing
 * behaviour is covered by {@code SecurityValidatorTest}.
 *
 * <p>
 * Not exercised: {@code scanSecurity} end to end. It shells out through the static
 * {@code ToolRunner}, which has no injectable seam, so the CLI plumbing — argv, exit
 * codes, the timeout, the fail-closed JSON handling behind
 * {@code test_invalid_skillspector_json_fails_closed} and
 * {@code test_unexpected_skillspector_exit_fails_closed} — is asserted through the pieces
 * it is made of ({@link SkillspectorSecurityScanner#POLICY_EXIT_CODES},
 * {@link SkillspectorSecurityScanner#timeoutSeconds()}) rather than a live run, and no
 * test here requires a SkillSpector binary.
 */
class SkillspectorSecurityScannerTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static JsonNode issue(String json) throws Exception {
		return MAPPER.readTree(json);
	}

	// ------------------------------------------------------------ conversion

	/** The issue from {@code test_skillspector_policy_exit_one_processes_findings}. */
	@Test
	void reportIssueBecomesAnUpstreamShapedFinding() throws Exception {
		Finding finding = SkillspectorSecurityScanner.toFinding(issue("""
				{
				  "id": "PI-1",
				  "category": "Prompt Injection",
				  "pattern": "Instruction override",
				  "severity": "HIGH",
				  "confidence": 1.0,
				  "finding": "Ignore prior instructions",
				  "location": {"file": "SKILL.md", "start_line": 8}
				}
				"""));

		assertEquals("SECURITY", finding.getCategory());
		assertEquals(Severity.HIGH, finding.getSeverity());
		assertEquals("Instruction override (PI-1)", finding.getCheckName());
		assertEquals("Prompt Injection: Ignore prior instructions", finding.getMessage());
		assertEquals("SKILL.md", finding.getFilePath());
		assertEquals(Integer.valueOf(8), finding.getLineNumber());
		assertNull(finding.getLineContent());
		assertNull(finding.getSuggestion());
		assertEquals(Map.of("skillspector_confidence", 1.0), finding.getMetadata());
	}

	/** Severity is upper-cased before parsing, and an unknown one lands on MEDIUM. */
	@Test
	void severityIsCaseInsensitiveWithAMediumFallback() throws Exception {
		Finding lower = SkillspectorSecurityScanner
			.toFinding(issue("{\"pattern\":\"p\",\"severity\":\"critical\",\"finding\":\"f\"}"));
		assertEquals(Severity.CRITICAL, lower.getSeverity());

		Finding unknown = SkillspectorSecurityScanner
			.toFinding(issue("{\"pattern\":\"p\",\"severity\":\"bogus\",\"finding\":\"f\"}"));
		assertEquals(Severity.MEDIUM, unknown.getSeverity());

		Finding absent = SkillspectorSecurityScanner.toFinding(issue("{\"pattern\":\"p\",\"finding\":\"f\"}"));
		assertEquals(Severity.MEDIUM, absent.getSeverity());
	}

	/** A missing or unusable {@code location} is {@code ("unknown", None)} upstream. */
	@Test
	void locationFallsBackToUnknownAndFallsBackToTheLineKey() throws Exception {
		Finding noLocation = SkillspectorSecurityScanner
			.toFinding(issue("{\"pattern\":\"p\",\"severity\":\"low\",\"finding\":\"f\"}"));
		assertEquals("unknown", noLocation.getFilePath());
		assertNull(noLocation.getLineNumber());

		Finding stringLocation = SkillspectorSecurityScanner
			.toFinding(issue("{\"pattern\":\"p\",\"finding\":\"f\",\"location\":\"SKILL.md\"}"));
		assertEquals("unknown", stringLocation.getFilePath());
		assertNull(stringLocation.getLineNumber());

		Finding lineKey = SkillspectorSecurityScanner
			.toFinding(issue("{\"pattern\":\"p\",\"finding\":\"f\",\"location\":{\"file\":\"\",\"line\":12}}"));
		assertEquals("unknown", lineKey.getFilePath());
		assertEquals(Integer.valueOf(12), lineKey.getLineNumber());
	}

	/** {@code start_line} wins over {@code line}, and a zero line is falsy in Python. */
	@Test
	void startLineWinsAndZeroIsNoLine() throws Exception {
		Finding both = SkillspectorSecurityScanner.toFinding(issue(
				"{\"pattern\":\"p\",\"finding\":\"f\",\"location\":{\"file\":\"a.md\",\"start_line\":7,\"line\":3}}"));
		assertEquals(Integer.valueOf(7), both.getLineNumber());

		Finding zero = SkillspectorSecurityScanner.toFinding(
				issue("{\"pattern\":\"p\",\"finding\":\"f\",\"location\":{\"file\":\"a.md\",\"start_line\":0}}"));
		assertNull(zero.getLineNumber());
	}

	/**
	 * {@code code_snippet} is Python's {@code [:200]}: a code-point bound, not a UTF-16
	 * one.
	 */
	@Test
	void codeSnippetIsTruncatedByCodePoint() throws Exception {
		String astral = "\uD83D\uDE00"; // one code point, two UTF-16 units
		String snippet = "x".repeat(199) + astral + "tail";

		Finding finding = SkillspectorSecurityScanner
			.toFinding(issue("{\"pattern\":\"p\",\"finding\":\"f\",\"code_snippet\":\"" + snippet + "\"}"));

		String content = finding.getLineContent();
		assertEquals(200, content.codePointCount(0, content.length()));
		assertTrue(content.endsWith(astral));
		assertEquals(snippet, SkillspectorSecurityScanner.truncate(snippet, 300));
	}

	/**
	 * {@code suggestion} is {@code f"{explanation} {remediation}".strip() or None} — and
	 * {@code str.strip()} only trims the ends, so the padding between the two survives.
	 */
	@Test
	void suggestionJoinsExplanationAndRemediationAndCollapsesToNull() throws Exception {
		Finding both = SkillspectorSecurityScanner.toFinding(
				issue("{\"pattern\":\"p\",\"finding\":\"f\",\"explanation\":\" why \",\"remediation\":\" how \"}"));
		assertEquals("why   how", both.getSuggestion());

		Finding neither = SkillspectorSecurityScanner
			.toFinding(issue("{\"pattern\":\"p\",\"finding\":\"f\",\"explanation\":\"\",\"remediation\":\"   \"}"));
		assertNull(neither.getSuggestion());
	}

	/**
	 * A falsy field falls back the way Python's {@code issue.get(key) or default} does.
	 */
	@Test
	void falsyFieldsTakeTheirDefaults() throws Exception {
		Finding finding = SkillspectorSecurityScanner
			.toFinding(issue("{\"pattern\":\"\",\"id\":\"\",\"severity\":\"\",\"finding\":\"f\"}"));
		assertEquals("Unknown (?)", finding.getCheckName());
		assertEquals("f", finding.getMessage());
	}

	// --------------------------------------------------------------- messages

	@Test
	void issueMessagePrefersFindingThenExplanationThenPattern() {
		assertEquals("Cat: the finding",
				SkillspectorSecurityScanner.buildIssueMessage("Cat", "the finding", "why", "pat"));
		assertEquals("the finding", SkillspectorSecurityScanner.buildIssueMessage("", "the finding", "why", "pat"));
		assertEquals("Cat: why", SkillspectorSecurityScanner.buildIssueMessage("Cat", "", "why", "pat"));
		assertEquals("why", SkillspectorSecurityScanner.buildIssueMessage("", "", "why", "pat"));
		assertEquals("pat", SkillspectorSecurityScanner.buildIssueMessage("Cat", "", "", "pat"));
	}

	/**
	 * The explanation branch is clipped at 120 characters, like
	 * {@code explanation[:120]}.
	 */
	@Test
	void explanationIsClippedAt120CodePoints() {
		String rambling = "a".repeat(150);
		String message = SkillspectorSecurityScanner.buildIssueMessage("Cat", "", rambling, "pat");
		assertEquals("Cat: " + "a".repeat(120), message);
	}

	// --------------------------------------------------------------- metadata

	@Test
	void metadataCarriesConfidenceAndIntentOnlyWhenPresent() throws Exception {
		Map<String, Object> full = SkillspectorSecurityScanner
			.issueMetadata(issue("{\"confidence\":0.8,\"intent\":\"Automate reports\"}"));
		assertEquals(0.8, full.get("skillspector_confidence"));
		assertEquals("Automate reports", full.get("intent"));

		// Python tests confidence with `is not None`, so a zero or false one is kept.
		Map<String, Object> zero = SkillspectorSecurityScanner.issueMetadata(issue("{\"confidence\":0}"));
		assertEquals(0L, zero.get("skillspector_confidence"));

		// An empty intent is falsy and drops out; a null confidence does too.
		Map<String, Object> empty = SkillspectorSecurityScanner
			.issueMetadata(issue("{\"confidence\":null,\"intent\":\"\"}"));
		assertTrue(empty.isEmpty());
	}

	// ------------------------------------------------------------- plumbing

	@Test
	void scannerIdentityMatchesTheToolRegistryName() {
		SkillspectorSecurityScanner scanner = new SkillspectorSecurityScanner();
		assertEquals("skillspector", scanner.getName());
		assertEquals("skillspector", SkillspectorSecurityScanner.TOOL_NAME);
	}

	/**
	 * Exit codes 0 and 1 are the scanner's policy codes; anything else fails the stage.
	 */
	@Test
	void policyExitCodesAreCleanAndFindings() {
		assertEquals(List.of(0, 1), SkillspectorSecurityScanner.POLICY_EXIT_CODES);
		assertFalse(SkillspectorSecurityScanner.POLICY_EXIT_CODES.contains(2));
	}

	/** Upstream's default watchdog is 1200 s, overridable through the environment. */
	@Test
	void timeoutDefaultsToTheUpstreamWatchdog() {
		Assumptions.assumeTrue(System.getenv(SkillspectorSecurityScanner.TIMEOUT_ENV_VAR) == null,
				SkillspectorSecurityScanner.TIMEOUT_ENV_VAR + " is set in this environment");
		assertEquals(1200, SkillspectorSecurityScanner.DEFAULT_TIMEOUT_SECONDS);
		assertEquals(1200L, SkillspectorSecurityScanner.timeoutSeconds());
	}

	/** The hint names the pinned wheel rather than the release this port tracks. */
	@Test
	void installHintNamesThePinnedWheel() {
		assertTrue(SkillspectorSecurityScanner.INSTALL_HINT.startsWith("Install with: uv tool install "));
		assertTrue(SkillspectorSecurityScanner.INSTALL_HINT.contains("skillspector-2.13.4-py3-none-any.whl"));
	}

}
