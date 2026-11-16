/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.iromu.embabel.smith.skillevaluator.model.Finding;
import org.iromu.embabel.smith.skillevaluator.model.Severity;
import org.iromu.embabel.smith.skillevaluator.model.SuccessDetail;
import org.iromu.embabel.smith.skillevaluator.model.ValidationResult;

/**
 * Ports of the CodeRisk cases from {@code tests/validators/test_security_validators.py}
 * ({@code TestCodeRiskValidator}) and
 * {@code tests/validators/test_code_risk_structured.py}.
 *
 * <p>
 * The Python suite patches {@code Tools.bandit}/{@code Tools.semgrep}; the Java port
 * injects fakes through the package-private {@link CodeRiskValidator} seam instead.
 * Environment-variable behaviour that Python exercises with monkeypatch
 * ({@code SKILLEVALUATOR_REQUIRE_SEMGREP}) is covered through the extracted
 * {@link CodeRiskValidator#requireSemgrep(String)} predicate.
 */
class CodeRiskValidatorTest {

	@TempDir
	Path tmp;

	// ------------------------------------------------------------- fakes & fixtures

	/** Scripted stand-in for one scanner CLI, mirroring the Python ToolResult. */
	private static final class FakeTool implements CodeRiskValidator.ScannerTool {

		final List<List<String>> recordedArgs = new ArrayList<>();

		final List<CodeRiskValidator.ScannerTool.RunResult> scripted = new ArrayList<>();

		boolean available = true;

		String version = "1.2.3";

		void enqueue(boolean success, int exitCode, String stdout, String stderr, String errorMessage) {
			scripted.add(new CodeRiskValidator.ScannerTool.RunResult(success, stdout, stderr, exitCode, errorMessage));
		}

		void enqueue(int exitCode, String stdout) {
			enqueue(true, exitCode, stdout, "", null);
		}

		CodeRiskValidator.ScannerTool.RunResult lastResult() {
			return scripted.get(Math.min(scripted.size(), recordedArgs.size()) - 1);
		}

		@Override
		public boolean isAvailable() {
			return available;
		}

		@Override
		public String version() {
			return available ? version : null;
		}

		@Override
		public RunResult run(List<String> args) {
			recordedArgs.add(List.copyOf(args));
			int index = recordedArgs.size() - 1;
			return index < scripted.size() ? scripted.get(index)
					: new RunResult(true, "{\"results\": [], \"errors\": []}", "", 0, null);
		}

	}

	private static CodeRiskValidator validator(boolean useSemgrep, boolean failOnLow, boolean excludeTests,
			FakeTool bandit, FakeTool semgrep) {
		return new CodeRiskValidator(useSemgrep, failOnLow, excludeTests, bandit, semgrep);
	}

	/**
	 * Python conftest {@code skill_with_security_issues}: SKILL.md plus a vulnerable
	 * script.py.
	 */
	private Path securityIssueSkill() throws IOException {
		Path skill = tmp.resolve("insecure-skill");
		Files.createDirectories(skill);
		Files.writeString(skill.resolve("SKILL.md"), """
				---
				name: insecure-skill
				description: A skill with security issues for testing detection
				---

				# Insecure Skill

				This skill has security issues.
				""");
		Files.writeString(skill.resolve("script.py"), """
				import os
				import subprocess

				# Hardcoded credentials
				api_key = "sk-1234567890abcdef1234567890abcdef12345678"
				password = "SuperSecret123"

				# Unsafe subprocess call
				subprocess.call("echo " + os.environ.get("TARGET", ""), shell=True)
				""");
		return skill;
	}

	/** Python conftest {@code sample_skill_dir}: a valid skill with no code files. */
	private Path sampleSkill() throws IOException {
		Path skill = tmp.resolve("skills").resolve("sample-skill");
		Files.createDirectories(skill);
		Files.writeString(skill.resolve("SKILL.md"),
				"""
						---
						name: sample-skill
						description: A sample skill for testing SkillEvaluator validation. Use when demonstrating validation workflows.
						metadata:
						  author: Test User <testuser@example.com>
						---

						# Sample Skill
						""");
		return skill;
	}

	private static boolean anyContains(List<String> haystack, String needle) {
		return haystack.stream().anyMatch(item -> item.contains(needle));
	}

	private static List<String> excludeValues(List<String> args) {
		List<String> out = new ArrayList<>();
		for (int i = 0; i < args.size() - 1; i++) {
			if ("--exclude".equals(args.get(i))) {
				out.add(args.get(i + 1));
			}
		}
		return out;
	}

	private static final String BANDIT_CLEAN = "{\"results\": [], \"metrics\": {}}";

	private static final String SEMGREP_CLEAN = "{\"results\": [], \"errors\": []}";

	// ------------------------------------------------------------------ initialization

	@Test
	void initDefault() {
		CodeRiskValidator validator = new CodeRiskValidator();
		assertEquals("Code Risk Analysis", validator.getName());
		assertEquals("Static code analysis using Bandit and packaged Semgrep rules", validator.getDescription());
		assertTrue(validator.isUseSemgrep());
		assertTrue(validator.isExcludeTests());
		assertFalse(validator.isFailOnLow());
	}

	@Test
	void initCustom() {
		CodeRiskValidator validator = new CodeRiskValidator(false, true, false);
		assertFalse(validator.isUseSemgrep());
		assertFalse(validator.isExcludeTests());
		assertTrue(validator.isFailOnLow());
	}

	// ---------------------------------------------------------------------- discovery

	@Test
	void validateNoCodeFiles() throws IOException {
		CodeRiskValidator validator = new CodeRiskValidator();
		ValidationResult result = validator.validate(sampleSkill());

		assertTrue(result.isPassed());
		assertTrue(anyContains(result.getMessages(), "No code files found"));
		assertTrue(result.getSuccessDetails().stream().anyMatch(d -> d.getCheckName().equals("code_file_discovery")));
	}

	@Test
	void fileCountsSkipArtifactDirsAndMessageListsTypes() throws IOException {
		Path skill = securityIssueSkill(); // script.py
		Files.createDirectories(skill.resolve("scripts"));
		Files.writeString(skill.resolve("scripts").resolve("run.sh"), "#!/bin/sh\necho hi\n");
		Files.writeString(skill.resolve("scripts").resolve("app.ts"), "export const x = 1;\n");
		Files.createDirectories(skill.resolve("evals"));
		Files.writeString(skill.resolve("evals").resolve("snapshot.py"), "x = 1\n");

		FakeTool bandit = new FakeTool();
		FakeTool semgrep = new FakeTool();
		ValidationResult result = validator(false, false, true, bandit, semgrep).validate(skill);

		assertTrue(result.getMessages().contains("Found 1 Python, 1 Shell, 1 JavaScript/TypeScript files"));
		assertEquals(1, bandit.recordedArgs.size());
	}

	// ------------------------------------------------------------------ missing tools

	@Test
	void validateNoToolsInstalled() throws IOException {
		FakeTool bandit = new FakeTool();
		FakeTool semgrep = new FakeTool();
		bandit.available = false;
		semgrep.available = false;

		ValidationResult result = validator(true, false, true, bandit, semgrep).validate(securityIssueSkill());

		assertEquals("incomplete", result.getStatus());
		assertFalse(result.isPassed());
		assertEquals(List.of("bandit"), result.getIncompleteScans());
		assertTrue(anyContains(result.getWarnings(),
				"bandit not installed. Bundled with SkillEvaluator — reinstall with: uv tool install --reinstall"));
		assertTrue(anyContains(result.getMessages(), "Semgrep not installed; skipped."));
	}

	@Test
	void requireSemgrepFlagParsing() {
		assertTrue(CodeRiskValidator.requireSemgrep("1"));
		assertTrue(CodeRiskValidator.requireSemgrep(" TRUE "));
		assertTrue(CodeRiskValidator.requireSemgrep("Yes"));
		assertFalse(CodeRiskValidator.requireSemgrep(null));
		assertFalse(CodeRiskValidator.requireSemgrep(""));
		assertFalse(CodeRiskValidator.requireSemgrep("0"));
		assertFalse(CodeRiskValidator.requireSemgrep("maybe"));
	}

	// ------------------------------------------------------------------------ bandit

	@Test
	void validateBanditClean() throws IOException {
		FakeTool bandit = new FakeTool();
		bandit.enqueue(0, BANDIT_CLEAN);
		FakeTool semgrep = new FakeTool();

		ValidationResult result = validator(false, false, true, bandit, semgrep).validate(securityIssueSkill());

		assertTrue(result.isPassed());
		assertTrue(result.getSuccessDetails().stream().anyMatch(d -> d.getCheckName().equals("bandit")));
		assertTrue(anyContains(result.getMessages(), "Bandit: No security issues found"));
		assertEquals(Map.of("bandit", "1.2.3"), result.getScanners());
	}

	@Test
	void validateBanditFindings() throws IOException {
		FakeTool bandit = new FakeTool();
		bandit.enqueue(1, """
				{
				    "results": [
				        {
				            "test_id": "B102",
				            "test_name": "exec_used",
				            "issue_severity": "HIGH",
				            "issue_confidence": "HIGH",
				            "issue_text": "Use of exec detected.",
				            "filename": "script.py",
				            "line_number": 12,
				            "issue_cwe": {"id": 78}
				        }
				    ],
				    "metrics": {}
				}""");

		ValidationResult result = validator(false, false, true, bandit, new FakeTool()).validate(securityIssueSkill());

		assertFalse(result.isPassed());
		assertTrue(anyContains(result.getErrors(), "BANDIT"));
		assertTrue(anyContains(result.getErrors(), "[BANDIT-HIGH] Use of exec detected. (CWE-78) in script.py:12"));
	}

	/** Ports test_code_risk_structured.TestBanditStructuredFindings. */
	@Test
	void banditStructuredFindings() {
		ValidationResult result = new ValidationResult();
		new CodeRiskValidator().processBanditOutput("""
				{
				    "results": [
				        {
				            "issue_severity": "HIGH",
				            "issue_confidence": "HIGH",
				            "issue_cwe": {"id": 78},
				            "issue_text": "subprocess call with shell=True identified, security issue.",
				            "test_id": "B602",
				            "test_name": "subprocess_popen_with_shell_equals_true",
				            "filename": "/tmp/test/bad.py",
				            "line_number": 6
				        },
				        {
				            "issue_severity": "LOW",
				            "issue_confidence": "MEDIUM",
				            "issue_cwe": {"id": 259},
				            "issue_text": "Possible hardcoded password: 'SuperSecret123'",
				            "test_id": "B105",
				            "test_name": "hardcoded_password_string",
				            "filename": "/tmp/test/bad.py",
				            "line_number": 9
				        }
				    ]
				}""", result, 1);

		assertEquals(2, result.getFindings().size());
		Finding first = result.getFindings().get(0);
		assertEquals("/tmp/test/bad.py", first.getFilePath());
		assertEquals(Severity.HIGH, first.getSeverity());
		assertEquals("B602:subprocess_popen_with_shell_equals_true", first.getCheckName());
		assertEquals("Review subprocess_popen_with_shell_equals_true (B602) (CWE-78)", first.getSuggestion());
		assertEquals("78", first.getMetadata().get("cwe"));
		Finding second = result.getFindings().get(1);
		assertEquals(Severity.LOW, second.getSeverity());
		assertEquals("B105:hardcoded_password_string", second.getCheckName());
		// LOW + MEDIUM confidence is a warning, not an error.
		assertTrue(anyContains(result.getWarnings(), "[BANDIT-LOW]"));
		assertFalse(anyContains(result.getErrors(), "[BANDIT-LOW]"));
		assertTrue(anyContains(result.getMessages(), "Bandit found: 1 HIGH, 0 MEDIUM, 1 LOW"));
	}

	@Test
	void banditCrashIsNotACleanPass() throws IOException {
		FakeTool bandit = new FakeTool();
		bandit.enqueue(true, 2, "", "usage: bandit [-h] [-r] ... error: unrecognized arguments", null);

		ValidationResult result = validator(false, false, true, bandit, new FakeTool()).validate(securityIssueSkill());

		assertFalse(anyContains(result.getMessages(), "No security issues found"));
		assertTrue(anyContains(result.getWarnings(), "Bandit error (exit code 2)"));
		assertEquals(List.of("bandit"), result.getIncompleteScans());
	}

	@Test
	void banditCommandUsesResolvedPathAndCombinedExcludes() throws IOException {
		FakeTool bandit = new FakeTool();
		bandit.enqueue(0, BANDIT_CLEAN);
		Path skill = securityIssueSkill();

		validator(false, false, true, bandit, new FakeTool()).validate(skill);

		List<String> args = bandit.recordedArgs.get(0);
		assertEquals("-r", args.get(0));
		assertEquals(skill.toAbsolutePath().normalize().toString(), args.get(1));
		assertTrue(args.containsAll(List.of("-f", "json", "-q", "-ll")));
		String exclude = args.get(args.indexOf("--exclude") + 1);
		assertTrue(exclude.startsWith("tests,test,*_test.py,test_*.py,"));
		assertTrue(exclude.contains("evals"));
		assertTrue(exclude.contains("node_modules"));
	}

	@Test
	void banditExcludeDropsTestPatternsWhenIncluded() throws IOException {
		FakeTool bandit = new FakeTool();
		bandit.enqueue(0, BANDIT_CLEAN);

		validator(false, false, false, bandit, new FakeTool()).validate(securityIssueSkill());

		String exclude = bandit.recordedArgs.get(0).get(bandit.recordedArgs.get(0).indexOf("--exclude") + 1);
		assertFalse(exclude.contains("test"));
	}

	// ----------------------------------------------------------------------- semgrep

	/** Ports test_semgrep_default_command_uses_packaged_offline_rules. */
	@Test
	void semgrepDefaultCommandUsesPackagedOfflineRules() throws IOException {
		FakeTool semgrep = new FakeTool();
		semgrep.enqueue(0, SEMGREP_CLEAN);

		validator(true, false, true, new FakeTool(), semgrep).runSemgrep(securityIssueSkill());

		List<String> args = semgrep.recordedArgs.get(0);
		int configIndex = args.indexOf("--config");
		Path configPath = Path.of(args.get(configIndex + 1));
		assertEquals("semgrep_rules.yaml", configPath.getFileName().toString());
		assertTrue(Files.isRegularFile(configPath), "packaged rules must exist on disk: " + configPath);
		assertFalse(configPath.toString().startsWith("p/"));
		assertFalse(configPath.toString().startsWith("r/"));
		assertEquals("off", args.get(args.indexOf("--metrics") + 1));
		assertTrue(args.contains("--disable-version-check"));
		assertTrue(args.contains("--no-rewrite-rule-ids"));
		assertTrue(args.contains("--error"));
		assertEquals("scan", args.get(0));
	}

	/** Ports test_run_semgrep_excludes_canonical_generated_benchmark + artifact dirs. */
	@Test
	void semgrepExcludesCanonicalGeneratedFiles() throws IOException {
		FakeTool semgrep = new FakeTool();
		semgrep.enqueue(0, SEMGREP_CLEAN);
		Path skill = tmp.resolve("test");
		Files.createDirectories(skill);
		Files.writeString(skill.resolve("BENCHMARK.md"), "generated");

		validator(true, false, true, new FakeTool(), semgrep).runSemgrep(skill);

		List<String> excludes = excludeValues(semgrep.recordedArgs.get(0));
		assertTrue(excludes.contains("BENCHMARK.md"));
		assertFalse(excludes.contains("benchmark.md"));
		assertFalse(excludes.contains("benchmarks.md"));
		assertTrue(excludes.contains("tests"));
		assertTrue(excludes.contains("test"));
		assertTrue(excludes.containsAll(List.of("evals", ".evals", "results", "versions", "node_modules")));
		// The scanned target is the resolved skill path and comes last.
		assertEquals(skill.toAbsolutePath().normalize().toString(),
				semgrep.recordedArgs.get(0).get(semgrep.recordedArgs.get(0).size() - 1));
	}

	/**
	 * Ports test_run_semgrep_follows_filesystem_case_aliases (case-sensitive filesystem
	 * semantics).
	 */
	@Test
	void semgrepKeepsAuthoredLowercaseAliasOffTheExcludeList() throws IOException {
		FakeTool semgrep = new FakeTool();
		semgrep.enqueue(0, SEMGREP_CLEAN);
		Path skill = tmp.resolve("test");
		Files.createDirectories(skill);
		Files.writeString(skill.resolve("benchmark.md"), "case-sensitive authored or case-insensitive generated");

		validator(true, false, true, new FakeTool(), semgrep).runSemgrep(skill);

		List<String> excludes = excludeValues(semgrep.recordedArgs.get(0));
		// On a case-sensitive filesystem the lowercase file is authored content, not the
		// generated BENCHMARK.md alias, so it must NOT be excluded.
		assertFalse(excludes.contains("benchmark.md"));
	}

	@Test
	void validateSemgrepTimeoutIsNotACleanPass() throws IOException {
		FakeTool bandit = new FakeTool();
		bandit.enqueue(0, BANDIT_CLEAN);
		FakeTool semgrep = new FakeTool();
		semgrep.enqueue(false, -1, "", "", "Semgrep timed out after 180 seconds");

		ValidationResult result = validator(true, false, true, bandit, semgrep).validate(securityIssueSkill());

		assertFalse(result.isPassed());
		assertTrue(result.getErrors().contains("Semgrep timed out after 180 seconds"));
		assertEquals(List.of("semgrep"), result.getIncompleteScans());
	}

	@Test
	void validateSemgrepCrashIsNotACleanPass() throws IOException {
		FakeTool bandit = new FakeTool();
		bandit.enqueue(0, BANDIT_CLEAN);
		FakeTool semgrep = new FakeTool();
		semgrep.enqueue(true, 2, "", "", null);

		ValidationResult result = validator(true, false, true, bandit, semgrep).validate(securityIssueSkill());

		assertFalse(result.isPassed());
		assertFalse(anyContains(result.getMessages(), "Semgrep: No"));
		assertTrue(anyContains(result.getErrors(), "Semgrep error (exit code 2)"));
		assertTrue(anyContains(result.getErrors(), "no diagnostic output"));
	}

	static Stream<Arguments> untrustworthySemgrepReports() {
		return Stream.of(Arguments.of("", "did not return valid json"),
				Arguments.of("not json", "did not return valid json"),
				Arguments.of("{}", "missing required 'results' list"),
				Arguments.of("{\"results\": {}, \"errors\": []}", "field 'results' must be a list"),
				Arguments.of("{\"results\": []}", "missing required 'errors' list"),
				Arguments.of("{\"results\": [], \"errors\": {}}", "field 'errors' must be a list"),
				Arguments.of("{\"results\": [null], \"errors\": []}", "entries must be objects"));
	}

	/** Ports test_semgrep_rejects_untrustworthy_json_reports. */
	@ParameterizedTest
	@MethodSource("untrustworthySemgrepReports")
	void semgrepRejectsUntrustworthyJsonReports(String output, String expectedError) {
		ValidationResult result = new ValidationResult();

		new CodeRiskValidator().processSemgrepOutput(output, result);

		assertFalse(result.isPassed());
		assertTrue(result.getErrors().stream().anyMatch(error -> error.toLowerCase().contains(expectedError)));
		assertFalse(anyContains(result.getMessages(), "Semgrep: No"));
	}

	/** Ports test_semgrep_errors_without_findings_fail_validation. */
	@Test
	void semgrepErrorsWithoutFindingsFailValidation() {
		ValidationResult result = new ValidationResult();

		new CodeRiskValidator().processSemgrepOutput(
				"{\"results\": [], \"errors\": [{\"message\": \"invalid security-audit configuration\"}]}", result);

		assertFalse(result.isPassed());
		assertTrue(anyContains(result.getErrors(), "invalid security-audit configuration"));
		assertFalse(anyContains(result.getMessages(), "Semgrep: No"));
	}

	/** Ports test_semgrep_errors_and_findings_are_both_reported. */
	@Test
	void semgrepErrorsAndFindingsAreBothReported() {
		ValidationResult result = new ValidationResult();

		new CodeRiskValidator().processSemgrepOutput("""
				{
				    "results": [
				        {
				            "check_id": "python.lang.security.audit.dangerous-system-call",
				            "path": "script.py",
				            "start": {"line": 10},
				            "extra": {
				                "severity": "ERROR",
				                "message": "Dangerous system call detected",
				                "metadata": {"cwe": ["CWE-78"]}
				            }
				        }
				    ],
				    "errors": [{"message": "one file could not be parsed"}]
				}""", result);

		assertFalse(result.isPassed());
		assertTrue(anyContains(result.getErrors(), "one file could not be parsed"));
		assertTrue(result.getFindings()
			.stream()
			.anyMatch(f -> f.getCheckName().equals("python.lang.security.audit.dangerous-system-call")));
	}

	/** Ports test_semgrep_parse_errors_report_every_path_and_stay_incomplete. */
	@Test
	void semgrepParseErrorsReportEveryPathAndStayIncomplete() {
		ValidationResult result = new ValidationResult();
		List<String> errors = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			errors.add("{\"code\": 3, \"level\": \"warn\", \"type\": \"SyntaxError\", \"path\": \"scripts/file%d.sh\","
				.formatted(i)
					+ " \"message\": \"Syntax error\", \"spans\": [{\"start\": {\"line\": %d, \"col\": 1}}]}"
						.formatted(i + 1));
		}
		errors.add(
				"{\"type\": [\"PartialParsing\", []], \"path\": \"scripts/copy.mjs\", \"message\": \"Partial parsing\"}");
		String output = "{\"results\": [], \"errors\": [" + String.join(",", errors) + "]}";

		new CodeRiskValidator().processSemgrepOutput(output, result);

		assertFalse(result.isPassed());
		assertEquals(List.of("semgrep"), result.getIncompleteScans());
		@SuppressWarnings("unchecked")
		List<String> unparsed = (List<String>) result.getMetadata().get("semgrep_unparsed_files");
		assertEquals("scripts/file0.sh", unparsed.get(0));
		assertEquals(6, unparsed.size());
		List<String> summaries = result.getErrors()
			.stream()
			.filter(error -> error.contains("Semgrep partial scan"))
			.toList();
		assertEquals(1, summaries.size());
		assertTrue(summaries.get(0).contains("6 file(s) could not be fully parsed"));
		for (int i = 0; i < 5; i++) {
			assertTrue(summaries.get(0).contains("scripts/file%d.sh:%d".formatted(i, i + 1)));
		}
		assertTrue(summaries.get(0).contains("scripts/copy.mjs"));
		assertFalse(summaries.get(0).contains("were not scanned"));
		assertFalse(anyContains(result.getErrors(), "Semgrep scan error"));
	}

	/** Ports test_semgrep_parse_error_listing_is_capped. */
	@Test
	void semgrepParseErrorListingIsCapped() {
		ValidationResult result = new ValidationResult();
		List<String> errors = new ArrayList<>();
		for (int i = 0; i < 25; i++) {
			errors
				.add("{\"type\": \"SyntaxError\", \"path\": \"s/f%d.sh\", \"message\": \"Syntax error\"}".formatted(i));
		}

		new CodeRiskValidator()
			.processSemgrepOutput("{\"results\": [], \"errors\": [" + String.join(",", errors) + "]}", result);

		@SuppressWarnings("unchecked")
		List<String> unparsed = (List<String>) result.getMetadata().get("semgrep_unparsed_files");
		assertEquals(25, unparsed.size());
		assertTrue(anyContains(result.getErrors(), "and 5 more"));
	}

	/** Ports test_semgrep_parse_errors_count_files_not_locations. */
	@Test
	void semgrepParseErrorsCountFilesNotLocations() {
		ValidationResult result = new ValidationResult();

		new CodeRiskValidator().processSemgrepOutput("""
				{"results": [], "errors": [
				    {"type": "SyntaxError", "path": "scripts/a.sh", "spans": [{"start": {"line": 3}}]},
				    {"type": "SyntaxError", "path": "scripts/a.sh", "spans": [{"start": {"line": 9}}]},
				    {"type": "SyntaxError", "path": "scripts/b.sh"}
				]}""", result);

		@SuppressWarnings("unchecked")
		List<String> unparsed = (List<String>) result.getMetadata().get("semgrep_unparsed_files");
		assertEquals(List.of("scripts/a.sh", "scripts/b.sh"), unparsed);
		String summary = result.getErrors()
			.stream()
			.filter(error -> error.contains("Semgrep partial scan"))
			.findFirst()
			.orElseThrow();
		assertTrue(summary.contains("2 file(s)"));
		assertTrue(summary.contains("scripts/a.sh:3"));
		assertFalse(summary.contains("scripts/a.sh:9"));
	}

	/** Ports test_semgrep_parse_errors_use_paths_relative_to_the_skill. */
	@Test
	void semgrepParseErrorsUsePathsRelativeToTheSkill() throws IOException {
		Path skill = tmp.resolve("my-skill").resolve("scripts");
		Files.createDirectories(skill);
		Path elsewhere = tmp.resolve("private-home").resolve("other.sh");
		List<String> errors = List.of(
				"{\"type\": \"SyntaxError\", \"path\": \"%s\", \"spans\": [{\"start\": {\"line\": 3}}]}"
					.formatted(skill.resolve("a.sh").toString().replace("\\", "/")),
				"{\"type\": \"SyntaxError\", \"path\": \"%s\"}".formatted(elsewhere.toString().replace("\\", "/")));
		ValidationResult result = new ValidationResult();

		new CodeRiskValidator().processSemgrepOutput(
				"{\"results\": [], \"errors\": [" + String.join(",", errors) + "]}", result, null,
				tmp.resolve("my-skill").toAbsolutePath().normalize());

		@SuppressWarnings("unchecked")
		List<String> unparsed = (List<String>) result.getMetadata().get("semgrep_unparsed_files");
		assertEquals(List.of("scripts/a.sh", "other.sh"), unparsed);
		String summary = result.getErrors()
			.stream()
			.filter(error -> error.contains("Semgrep partial scan"))
			.findFirst()
			.orElseThrow();
		assertTrue(summary.contains("scripts/a.sh:3"));
		assertFalse(summary.contains(tmp.toString()));
		assertTrue(unparsed.stream().noneMatch(path -> path.contains(tmp.toString())));
	}

	/** Ports test_semgrep_partial_parsing_error_in_semgrep_json_shape. */
	@Test
	void semgrepPartialParsingErrorInSemgrepJsonShape() {
		ValidationResult result = new ValidationResult();

		new CodeRiskValidator().processSemgrepOutput(
				"""
						{"results": [], "errors": [{
						    "code": 3,
						    "level": "warn",
						    "type": ["PartialParsing", [{"path": "scripts/bad.sh", "start": {"line": 2, "col": 1, "offset": 0}}]],
						    "path": "scripts/bad.sh",
						    "spans": [{"start": {"line": 2, "col": 1, "offset": 0}, "end": {"line": 3, "col": 10, "offset": 23}}]
						}]}""",
				result);

		assertEquals(List.of("semgrep"), result.getIncompleteScans());
		String summary = result.getErrors()
			.stream()
			.filter(error -> error.contains("Semgrep partial scan"))
			.findFirst()
			.orElseThrow();
		assertTrue(summary.contains("scripts/bad.sh:2"));
		assertTrue(summary.contains("skips the code it cannot parse"));
	}

	/** Ports test_run_semgrep_reports_parse_errors_with_relative_paths. */
	@Test
	void runSemgrepReportsParseErrorsWithRelativePaths() throws IOException {
		Path skill = tmp.resolve("my-skill").resolve("scripts");
		Files.createDirectories(skill);
		String absolute = skill.resolve("bad.sh").toAbsolutePath().normalize().toString().replace("\\", "/");
		FakeTool semgrep = new FakeTool();
		semgrep.enqueue(0,
				"{\"results\": [], \"errors\": [{\"type\": \"SyntaxError\", \"path\": \"%s\",".formatted(absolute)
						+ " \"spans\": [{\"start\": {\"line\": 2}}]}]}");

		ValidationResult result = validator(true, false, true, new FakeTool(), semgrep).runSemgrep(skill.getParent());

		assertEquals(List.of("semgrep"), result.getIncompleteScans());
		@SuppressWarnings("unchecked")
		List<String> unparsed = (List<String>) result.getMetadata().get("semgrep_unparsed_files");
		assertEquals(List.of("scripts/bad.sh"), unparsed);
		assertTrue(result.getErrors().stream().noneMatch(error -> error.contains(tmp.toString())));
	}

	/** Ports test_semgrep_parse_errors_keep_findings_and_other_errors. */
	@Test
	void semgrepParseErrorsKeepFindingsAndOtherErrors() {
		ValidationResult result = new ValidationResult();

		new CodeRiskValidator().processSemgrepOutput("""
				{
				    "results": [
				        {
				            "check_id": "rule.id",
				            "path": "a.py",
				            "start": {"line": 1},
				            "extra": {"severity": "ERROR", "message": "bad", "metadata": {}}
				        }
				    ],
				    "errors": [
				        {"type": "SyntaxError", "path": "run.sh", "message": "Syntax error"},
				        {"message": "invalid security-audit configuration"}
				    ]
				}""", result);

		assertTrue(result.getErrors()
			.stream()
			.anyMatch(error -> error.contains("Semgrep partial scan") && error.contains("run.sh")));
		assertTrue(anyContains(result.getErrors(), "invalid security-audit configuration"));
		assertTrue(result.getFindings().stream().anyMatch(f -> f.getCheckName().equals("rule.id")));
		assertEquals(List.of("semgrep"), result.getIncompleteScans());
	}

	/** Ports test_semgrep_accepts_valid_clean_report. */
	@Test
	void semgrepAcceptsValidCleanReport() {
		ValidationResult result = new ValidationResult();

		new CodeRiskValidator().processSemgrepOutput("{\"results\": [], \"errors\": []}", result);

		assertTrue(result.isPassed());
		assertTrue(result.getErrors().isEmpty());
		assertTrue(result.getSuccessDetails().stream().anyMatch(d -> d.getCheckName().equals("semgrep")));
		assertEquals(List.of("[OK] semgrep: Semgrep: No security issues found"), result.getMessages());
	}

	/** Ports the structured part of test_run_semgrep_end_to_end. */
	@Test
	void semgrepStructuredFindings() {
		ValidationResult result = new ValidationResult();

		new CodeRiskValidator().processSemgrepOutput("""
				{
				    "results": [
				        {
				            "check_id": "python.lang.security.audit.subprocess-shell-true",
				            "path": "/tmp/test/bad.py",
				            "start": {"line": 6},
				            "extra": {
				                "severity": "ERROR",
				                "message": "subprocess call with shell=True",
				                "metadata": {"cwe": ["CWE-78"], "owasp": ["A1:2017"]}
				            }
				        }
				    ],
				    "errors": []
				}""", result, 0, null);

		assertEquals(1, result.getFindings().size());
		Finding finding = result.getFindings().get(0);
		assertEquals("SEMGREP", finding.getCategory());
		assertEquals("/tmp/test/bad.py", finding.getFilePath());
		assertEquals(Severity.HIGH, finding.getSeverity());
		assertEquals("python.lang.security.audit.subprocess-shell-true", finding.getCheckName());
		assertEquals(6, finding.getLineNumber());
		assertEquals("CWE: CWE-78; OWASP: A1:2017", finding.getSuggestion());
		// exit 0 with findings: scanner status inconsistency is reported, findings kept.
		assertTrue(anyContains(result.getErrors(),
				"Semgrep returned findings with clean exit code 0; scanner status is inconsistent"));
	}

	@Test
	void semgrepExitOneWithoutFindingsIsIncomplete() {
		ValidationResult result = new ValidationResult();

		new CodeRiskValidator().processSemgrepOutput("{\"results\": [], \"errors\": []}", result, 1, null);

		assertTrue(anyContains(result.getErrors(), "Semgrep findings exit code 1 did not include findings"));
		assertFalse(anyContains(result.getMessages(), "Semgrep: No"));
	}

	/** Ports test_validate_semgrep_findings (legacy string shaping). */
	@Test
	void validateSemgrepFindings() throws IOException {
		FakeTool bandit = new FakeTool();
		bandit.enqueue(0, BANDIT_CLEAN);
		FakeTool semgrep = new FakeTool();
		semgrep.enqueue(1, """
				{
				    "results": [
				        {
				            "check_id": "python.lang.security.audit.dangerous-system-call",
				            "path": "script.py",
				            "start": {"line": 10},
				            "extra": {
				                "severity": "ERROR",
				                "message": "Dangerous system call detected",
				                "metadata": {"cwe": ["CWE-78"]}
				            }
				        }
				    ],
				    "errors": []
				}""");

		ValidationResult result = validator(true, false, true, bandit, semgrep).validate(securityIssueSkill());

		List<String> all = new ArrayList<>(result.getErrors());
		all.addAll(result.getWarnings());
		assertTrue(anyContains(all, "SEMGREP"));
		assertTrue(anyContains(all, "[SEMGREP-HIGH] Dangerous system call detected in script.py:10"));
		assertFalse(result.isPassed());
	}

	// ------------------------------------------------------------------- display path

	@Test
	void displayPathRelativeStaysPosix() {
		assertEquals("scripts/a.sh", CodeRiskValidator.semgrepDisplayPath("scripts/a.sh", null));
		assertEquals("a.sh", CodeRiskValidator.semgrepDisplayPath("a.sh", null));
	}

	@Test
	void displayPathAbsoluteOutsideSkillKeepsNameOnly() {
		Path skill = tmp.resolve("my-skill").toAbsolutePath().normalize();
		assertEquals("other.sh", CodeRiskValidator.semgrepDisplayPath(
				tmp.resolve("private-home").resolve("other.sh").toAbsolutePath().normalize().toString(), skill));
		assertEquals("scripts/a.sh", CodeRiskValidator
			.semgrepDisplayPath(skill.resolve("scripts/a.sh").toAbsolutePath().normalize().toString(), skill));
	}

	// ------------------------------------------------------------- scan incomplete glue

	@Test
	void mergeKeepsSemgrepMetadata() {
		ValidationResult merged = new ValidationResult();
		merged.merge(runSemgrepWithUnparsedMetadata("scripts/bad.sh"));
		merged.merge(runSemgrepWithUnparsedMetadata("scripts/other.sh"));

		assertEquals(List.of("semgrep"), merged.getIncompleteScans());
		@SuppressWarnings("unchecked")
		List<String> unparsed = (List<String>) merged.getMetadata().get("semgrep_unparsed_files");
		assertEquals(List.of("scripts/other.sh"), unparsed);
	}

	private static ValidationResult runSemgrepWithUnparsedMetadata(String file) {
		ValidationResult result = new ValidationResult();
		result.getMetadata().put("semgrep_unparsed_files", List.of(file));
		result.markScanIncomplete("semgrep");
		return result;
	}

	// ------------------------------------------------------------------ success detail

	@Test
	void codeFileDiscoverySuccessDetailCarriesCounts() throws IOException {
		ValidationResult result = new CodeRiskValidator().validate(sampleSkill());

		SuccessDetail detail = result.getSuccessDetails()
			.stream()
			.filter(d -> d.getCheckName().equals("code_file_discovery"))
			.findFirst()
			.orElseThrow();
		assertEquals("No code files found - code risk analysis is not applicable", detail.getMessage());
		assertEquals(Map.of("file_counts", Map.of("py", 0, "sh", 0, "js", 0)), detail.getMetadata());
	}

	@Test
	void semgrepSkippedWhenUseSemgrepDisabled() throws IOException {
		FakeTool semgrep = new FakeTool();
		validator(false, false, true, new FakeTool(), semgrep).validate(securityIssueSkill());
		assertTrue(semgrep.recordedArgs.isEmpty());
	}

}
