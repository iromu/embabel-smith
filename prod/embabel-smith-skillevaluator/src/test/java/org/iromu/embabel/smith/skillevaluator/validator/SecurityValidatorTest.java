/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.iromu.embabel.smith.skillevaluator.model.Finding;
import org.iromu.embabel.smith.skillevaluator.model.Severity;
import org.iromu.embabel.smith.skillevaluator.model.ValidationResult;
import org.iromu.embabel.smith.skillevaluator.spi.SecurityScanner;
import org.iromu.embabel.smith.skillevaluator.spi.SkillspectorSecurityScanner;

/**
 * The {@code security} check's folder-or-skill shell, ported from the skillspector-stage
 * cases in {@code tests/validators/test_security.py} ({@code TestSecurityValidator} — the
 * ones that describe what {@code validate_security_only} does with an engine's report
 * rather than how the CLI report is parsed).
 *
 * <p>
 * The Python suite patches {@code Tools.skillspector}; here a {@link SecurityScanner}
 * engine is injected, so no SkillSpector binary is needed and no test can shell out. What
 * the built-in engine itself does with a report — issue conversion, the policy exit
 * codes, the risk-score gate, the fail-closed JSON/exit-code handling behind
 * {@code test_invalid_skillspector_json_fails_closed} and
 * {@code test_unexpected_skillspector_exit_fails_closed} — is the engine's contract and
 * lives with it in {@code SkillspectorSecurityScannerTest}.
 *
 * <p>
 * Upstream cases that need the layers this port leaves out are not mirrored: baseline
 * suppression, partial/{@code analysis_completeness} reporting, the
 * {@code skillspector_hook_unverified} gate, content-type scoping, LLM verification, and
 * report normalization.
 */
class SecurityValidatorTest {

	@TempDir
	Path tmp;

	// ------------------------------------------------------------- fixtures

	/** A skill directory the folder walker will find. */
	private Path skill(String name) throws IOException {
		Path dir = tmp.resolve(name);
		Files.createDirectories(dir);
		Files.writeString(dir.resolve("SKILL.md"), """
				---
				name: %s
				description: A skill used to exercise the security check's folder driver.
				---

				# %s
				""".formatted(name, name), StandardCharsets.UTF_8);
		return dir;
	}

	/**
	 * Stand-in engine: hands back one canned result per skill and records what it
	 * scanned.
	 */
	private static final class FakeEngine implements SecurityScanner {

		final List<Path> scanned = new ArrayList<>();

		private final Supplier<ValidationResult> perSkill;

		boolean available = true;

		FakeEngine(Supplier<ValidationResult> perSkill) {
			this.perSkill = perSkill;
		}

		FakeEngine(ValidationResult one) {
			this(() -> one);
		}

		@Override
		public String getName() {
			return "skillspector";
		}

		@Override
		public boolean isAvailable() {
			return available;
		}

		@Override
		public ValidationResult scanSecurity(Path skillPath) {
			scanned.add(skillPath);
			return perSkill.get();
		}

	}

	/**
	 * One issue as SkillSpector reports it: {@code Instruction override (PI-1)} at
	 * SKILL.md:8.
	 */
	private static Finding skillspectorFinding(Severity severity) {
		return new Finding("SECURITY", severity, "Instruction override (PI-1)",
				"Prompt Injection: Ignore prior instructions", "SKILL.md", 8, null, "Remove the override text",
				Map.of("skillspector_confidence", 1.0));
	}

	private static ValidationResult reportWith(Finding finding) {
		ValidationResult result = new ValidationResult();
		result.recordScanner("skillspector", "2.13.4");
		result.addMessage("skillspector found 1 issue(s) including critical/high severity");
		result.addStructuredFinding(finding,
				finding.getSeverity() == Severity.CRITICAL || finding.getSeverity() == Severity.HIGH);
		return result;
	}

	private static boolean anyContains(List<String> haystack, String needle) {
		return haystack.stream().anyMatch(item -> item.contains(needle));
	}

	// ------------------------------------------------------------ identity

	@Test
	void nameAndDescriptionMatchUpstream() throws IOException {
		SecurityValidator validator = new SecurityValidator(new FakeEngine(new ValidationResult()));
		assertEquals("Security Scan", validator.getName());
		assertEquals("Detect security vulnerabilities and PII/secrets", validator.getDescription());

		ValidationResult result = validator.validateSecurityOnly(skill("named-skill"));
		assertEquals("Security Scan", result.getValidatorName());
		assertEquals("Detect security vulnerabilities and PII/secrets", result.getValidatorDescription());
	}

	/** The engine seam is what the validator scans with, and what it reports. */
	@Test
	void exposesInjectedEngine() {
		FakeEngine engine = new FakeEngine(new ValidationResult());
		assertSame(engine, new SecurityValidator(engine).getScanner());
	}

	/**
	 * With nothing registered through {@code ScannerRegistry}, the default engine is the
	 * built-in SkillSpector shell-out. Constructing it must not run it.
	 */
	@Test
	void defaultEngineIsTheSkillspectorCli() {
		assertTrue(new SecurityValidator().getScanner() instanceof SkillspectorSecurityScanner);
	}

	// ------------------------------------------------------- single-skill pass

	/**
	 * {@code validate()} is the {@code security} command: the scanner stage, nothing
	 * else.
	 */
	@Test
	void validateDelegatesToValidateSecurityOnly() throws IOException {
		Path dir = skill("delegating-skill");
		FakeEngine engine = new FakeEngine(() -> reportWith(skillspectorFinding(Severity.HIGH)));
		SecurityValidator validator = new SecurityValidator(engine);

		ValidationResult direct = validator.validateSecurityOnly(dir);
		ValidationResult throughValidate = validator.validate(dir);

		assertEquals(checkNames(direct), checkNames(throughValidate));
		assertEquals(direct.getMessages(), throughValidate.getMessages());
		assertEquals(direct.getStatus(), throughValidate.getStatus());
		assertEquals(2, engine.scanned.size());
	}

	private static List<String> checkNames(ValidationResult result) {
		return result.getFindings().stream().map(Finding::getCheckName).toList();
	}

	/**
	 * {@code test_skillspector_policy_exit_one_processes_findings} at the shell level: a
	 * finding arrives here exactly as the engine produced it, and a HIGH one fails the
	 * run.
	 */
	@Test
	void engineFindingsPassThroughUnchanged() throws IOException {
		ValidationResult result = new SecurityValidator(new FakeEngine(reportWith(skillspectorFinding(Severity.HIGH))))
			.validate(skill("injected-skill"));

		assertFalse(result.isPassed());
		assertEquals("failed", result.getStatus());
		Finding finding = result.getFindings().get(0);
		assertEquals("Instruction override (PI-1)", finding.getCheckName());
		assertEquals("Prompt Injection: Ignore prior instructions", finding.getMessage());
		assertEquals("SKILL.md", finding.getFilePath());
		assertEquals(Integer.valueOf(8), finding.getLineNumber());
		assertEquals(1.0, finding.getMetadata().get("skillspector_confidence"));
		assertEquals("2.13.4", result.getScanners().get("skillspector"));
		assertTrue(anyContains(result.getErrors(), "[SECURITY-HIGH] Prompt Injection: Ignore prior instructions"));
		assertTrue(anyContains(result.getMessages(), "skillspector found 1 issue(s)"));
	}

	/**
	 * {@code test_skillspector_unavailable_adds_warning}: an unavailable scanner warns
	 * and completes, and the result is incomplete rather than clean.
	 */
	@Test
	void unavailableEngineWarnsAndMarksScanIncomplete() throws IOException {
		ValidationResult unavailable = new ValidationResult();
		unavailable.addWarning("skillspector not installed - skipping security scan. "
				+ "Install with: uv tool install https://github.com/NVIDIA/SkillSpector/releases/download/"
				+ "v2.13.4/skillspector-2.13.4-py3-none-any.whl");
		unavailable.markScanIncomplete("skillspector");

		FakeEngine engine = new FakeEngine(unavailable);
		engine.available = false;
		ValidationResult result = new SecurityValidator(engine).validateSecurityOnly(skill("clean-skill"));

		assertTrue(anyContains(result.getWarnings(), "skillspector not installed"));
		assertEquals(List.of("skillspector"), result.getIncompleteScans());
		assertEquals("incomplete", result.getStatus());
		assertFalse(result.isPassed());
		assertTrue(result.getFindings().isEmpty());
		// A scanner that never ran has no version to report.
		assertTrue(result.getScanners().isEmpty());
	}

	// ------------------------------------------------------------ folder scan

	/** Every skill under a folder is scanned and reported under its own name. */
	@Test
	void folderScanAggregatesAndPrefixesSkills() throws IOException {
		skill("alpha");
		skill("beta");

		FakeEngine engine = new FakeEngine(() -> reportWith(skillspectorFinding(Severity.HIGH)));
		ValidationResult result = new SecurityValidator(engine).validate(tmp);

		assertEquals(2, engine.scanned.size());
		assertEquals(2, result.getFindings().size());
		assertTrue(result.getFindings()
			.stream()
			.allMatch(f -> f.getFilePath().startsWith("[") && f.getFilePath().contains("] SKILL.md")));
		assertTrue(anyContains(result.getErrors(), "[alpha] [SECURITY-HIGH]"));
		assertTrue(anyContains(result.getErrors(), "[beta] [SECURITY-HIGH]"));
		assertTrue(anyContains(result.getMessages(), "Security scanning 2 skill(s)"));
		assertFalse(result.isPassed());
	}

	/**
	 * A clean engine on every skill passes the folder, with each skill's success
	 * recorded.
	 */
	@Test
	void folderScanPassesWhenEverySkillIsClean() throws IOException {
		skill("alpha");
		skill("beta");

		ValidationResult clean = new ValidationResult();
		clean.addSuccess("skillspector", "No security vulnerabilities detected (secrets, API keys, credentials)");
		FakeEngine engine = new FakeEngine(clean);

		ValidationResult result = new SecurityValidator(engine).validate(tmp);

		assertEquals(2, engine.scanned.size());
		assertTrue(result.isPassed());
		assertEquals("passed", result.getStatus());
		List<String> checks = result.getSuccessDetails().stream().map(detail -> detail.getCheckName()).toList();
		assertEquals(List.of("skill_discovery", "alpha", "beta"), checks);
	}

	/**
	 * A CRITICAL finding stops the folder scan. Upstream exempts only its
	 * {@code skillspector_hook_unverified} gate from fail-fast, a report-shape check the
	 * minimal engine never produces, so this port exempts nothing.
	 */
	@Test
	void criticalFindingStopsFolderScan() throws IOException {
		skill("alpha");
		skill("beta");

		FakeEngine engine = new FakeEngine(() -> reportWith(skillspectorFinding(Severity.CRITICAL)));
		ValidationResult result = new SecurityValidator(engine).validate(tmp);

		assertEquals(1, engine.scanned.size());
		assertEquals(1, result.getFindings().size());
		assertTrue(anyContains(result.getErrors(), "[SECURITY-CRITICAL]"));
	}

	/** A folder with no manifests is scanned directly (the Python no-skills fallback). */
	@Test
	void folderWithoutSkillsFallsBackToFolderScan() throws IOException {
		Path folder = tmp.resolve("loose");
		Files.createDirectories(folder);
		Files.writeString(folder.resolve("notes.md"), "no manifest here\n");

		FakeEngine engine = new FakeEngine(new ValidationResult());
		ValidationResult result = new SecurityValidator(engine).validate(folder);

		// The "No skills found" warning lands on a result upstream throws away in this
		// branch (validators/base.py), so only the direct folder scan's own result
		// survives.
		assertEquals(List.of(folder), engine.scanned);
		assertTrue(result.isPassed());
	}

}
