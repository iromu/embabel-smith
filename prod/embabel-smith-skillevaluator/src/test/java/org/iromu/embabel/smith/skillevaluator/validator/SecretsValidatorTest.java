/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.iromu.embabel.smith.skillevaluator.model.Finding;
import org.iromu.embabel.smith.skillevaluator.model.Severity;
import org.iromu.embabel.smith.skillevaluator.model.ValidationResult;
import org.iromu.embabel.smith.skillevaluator.spi.GitleaksSecretsScanner;
import org.iromu.embabel.smith.skillevaluator.spi.SecretsScanner;
import org.iromu.embabel.smith.skillevaluator.spi.SecretsScannerTool;

/**
 * Ports of the secrets cases from {@code tests/validators/test_security_validators.py}
 * ({@code TestSecretsValidator}), {@code tests/validators/test_secrets_structured.py} and
 * {@code tests/validators/test_scan_incomplete.py}.
 *
 * <p>
 * The Python suite patches {@code Tools.gitleaks}; the Java port injects fakes instead —
 * a {@link SecretsScanner} engine for the validator shell, and a
 * {@link SecretsScannerTool} CLI for the built-in Gitleaks driver — so no binary is
 * needed. The skip-secrets interplay ({@code tests/test_skip_checks.py}) belongs to the
 * Tier 1 runner rather than this validator, and is not exercised here. The
 * report-conversion, severity-map and allowlist-config expectations live with the engine
 * in {@code GitleaksSecretsScannerTest}.
 */
class SecretsValidatorTest {

	@TempDir
	Path tmp;

	// ------------------------------------------------------------- fixtures

	/** Python conftest {@code sample_skill_dir}: a valid skill with no secrets. */
	private Path sampleSkill(String name) throws IOException {
		Path skill = tmp.resolve("skills").resolve(name);
		Files.createDirectories(skill);
		Files.writeString(skill.resolve("SKILL.md"),
				"""
						---
						name: sample-skill
						description: A sample skill for testing SkillEvaluator validation. Use when demonstrating validation workflows.
						---

						# Sample Skill
						""");
		return skill;
	}

	/**
	 * Python conftest {@code skill_with_security_issues}: SKILL.md plus a script holding
	 * a key.
	 */
	private Path secretSkill(String name) throws IOException {
		Path skill = tmp.resolve(name);
		Files.createDirectories(skill);
		Files.writeString(skill.resolve("SKILL.md"), """
				---
				name: insecure-skill
				description: A skill with security issues for testing detection
				---

				# Insecure Skill
				""");
		Files.writeString(skill.resolve("script.py"), "api_key = \"sk-1234567890abcdef1234567890abcdef12345678\"\n");
		return skill;
	}

	/**
	 * Stand-in engine: hands back one canned per-skill result, recording what it was
	 * asked to scan.
	 */
	private static final class FakeEngine implements SecretsScanner {

		final List<Path> scanned = new ArrayList<>();

		ValidationResult next = new ValidationResult();

		boolean available = true;

		FakeEngine(ValidationResult next) {
			this.next = next;
		}

		@Override
		public String getName() {
			return "fake";
		}

		@Override
		public boolean isAvailable() {
			return available;
		}

		@Override
		public ValidationResult scan(Path skillPath) {
			scanned.add(skillPath);
			return next;
		}

	}

	/**
	 * Scripted stand-in for the Gitleaks CLI, mirroring the Python {@code ToolResult}.
	 */
	private static final class FakeTool implements SecretsScannerTool {

		final List<List<String>> recordedArgs = new ArrayList<>();

		final List<Path> recordedCwd = new ArrayList<>();

		final List<Boolean> configExisted = new ArrayList<>();

		final List<SecretsScannerTool.RunResult> scripted = new ArrayList<>();

		boolean available = true;

		String version = "8.18.0";

		String installHint = "Install with: brew install gitleaks (macOS) or go install github.com/gitleaks/gitleaks/v8@latest";

		void enqueue(int exitCode, String stdout) {
			scripted.add(new SecretsScannerTool.RunResult(true, stdout, "", exitCode, null));
		}

		void enqueueError(String errorMessage) {
			scripted.add(new SecretsScannerTool.RunResult(false, "", "", -1, errorMessage));
		}

		@Override
		public String name() {
			return "Gitleaks";
		}

		@Override
		public String installHint() {
			return installHint;
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
		public RunResult run(List<String> args, Path cwd) {
			recordedArgs.add(List.copyOf(args));
			recordedCwd.add(cwd);
			int index = recordedArgs.size() - 1;
			int flag = args.indexOf("--config");
			configExisted.add(flag >= 0 && Files.exists(Path.of(args.get(flag + 1))));
			if (index < scripted.size()) {
				return scripted.get(index);
			}
			return new RunResult(true, "", "", 0, null);
		}

		List<String> lastArgs() {
			return recordedArgs.get(recordedArgs.size() - 1);
		}

		String argAfter(String flag) {
			List<String> args = lastArgs();
			return args.get(args.indexOf(flag) + 1);
		}

	}

	private static boolean anyContains(List<String> haystack, String needle) {
		return haystack.stream().anyMatch(item -> item.contains(needle));
	}

	// --------------------------------------------------- captured gitleaks report

	static final String AWS_KEY = "AKIA" + "IOSFODNN7EXAMPLE";
	static final String NVIDIA_KEY = "nvapi-" + "1234567890abcdef";

	/**
	 * {@code test_secrets_structured.py::GITLEAKS_OUTPUT} — a captured Gitleaks JSON
	 * report.
	 */
	static final String GITLEAKS_OUTPUT = """
			[
			  {
			    "Description": "Generic API Key",
			    "File": "/tmp/test/config.py",
			    "StartLine": 5,
			    "EndLine": 5,
			    "Match": "api_key = '%s'",
			    "Secret": "%s",
			    "RuleID": "generic-api-key",
			    "Tags": ["key", "api"]
			  },
			  {
			    "Description": "NVIDIA NGC API Key",
			    "File": "/tmp/test/config.py",
			    "StartLine": 8,
			    "EndLine": 8,
			    "Match": "ngc_key = '%s'",
			    "Secret": "%s",
			    "RuleID": "nvidia-ngc-api-key",
			    "Tags": ["key", "nvidia"]
			  }
			]
			""".formatted(AWS_KEY, AWS_KEY, NVIDIA_KEY, NVIDIA_KEY);

	// ---------------------------------------------------------- validator shell

	@Test
	void nameAndDescriptionMatchUpstream() {
		SecretsValidator validator = new SecretsValidator(new FakeEngine(new ValidationResult()));
		assertEquals("Secrets Detection", validator.getName());
		assertEquals("Detect hardcoded secrets, API keys, and credentials using Gitleaks", validator.getDescription());
	}

	@Test
	void gitHistoryModeIsEngineScoped() {
		assertFalse(new GitleaksSecretsScanner().isScanGitHistory());
		assertTrue(new GitleaksSecretsScanner(true).isScanGitHistory());
	}

	/** {@code test_validate_no_gitleaks_installed}. */
	@Test
	void unavailableEngineMarksScanIncomplete() throws IOException {
		ValidationResult unavailable = new ValidationResult();
		unavailable.addWarning("gitleaks not installed - secrets scanning skipped. "
				+ "Install with: brew install gitleaks (macOS) or go install github.com/gitleaks/gitleaks/v8@latest");
		unavailable.markScanIncomplete("gitleaks");

		ValidationResult result = new SecretsValidator(new FakeEngine(unavailable))
			.validate(sampleSkill("sample-skill"));

		assertEquals("incomplete", result.getStatus());
		assertFalse(result.isPassed());
		assertTrue(anyContains(result.getWarnings(), "gitleaks not installed"));
		assertEquals(List.of("gitleaks"), result.getIncompleteScans());
		assertEquals("Secrets Detection", result.getValidatorName());
		assertEquals("Detect hardcoded secrets, API keys, and credentials using Gitleaks",
				result.getValidatorDescription());
	}

	/** {@code test_validate_clean_skill}. */
	@Test
	void cleanEnginePassesAndRecordsSuccess() throws IOException {
		ValidationResult clean = new ValidationResult();
		clean.addSuccess("gitleaks", "No secrets detected by Gitleaks");
		clean.recordScanner("gitleaks", "8.18.0");

		ValidationResult result = new SecretsValidator(new FakeEngine(clean)).validate(sampleSkill("sample-skill"));

		assertTrue(result.isPassed());
		assertTrue(anyContains(result.getMessages(), "No secrets detected"));
		assertTrue(result.getSuccessDetails().stream().anyMatch(d -> d.getCheckName().equals("gitleaks")));
		assertEquals("8.18.0", result.getScanners().get("gitleaks"));
	}

	/** {@code test_validate_skill_with_secrets}: SECRET findings fail the run. */
	@Test
	void engineFindingsFailValidation() throws IOException {
		ValidationResult found = new ValidationResult();
		found.addMessage("Gitleaks found 1 potential secret(s)");
		found.addStructuredFinding(secretFinding(), true);

		ValidationResult result = new SecretsValidator(new FakeEngine(found)).validate(secretSkill("insecure"));

		assertFalse(result.isPassed());
		assertTrue(anyContains(result.getErrors(), "SECRET"));
		assertEquals("generic-api-key", result.getFindings().get(0).getCheckName());
	}

	private static Finding secretFinding() {
		return new Finding("SECRET", Severity.HIGH, "generic-api-key",
				"Generic API Key in script.py:5 [generic-api-key]", "script.py", 5, null,
				"Remove the secret from script.py, revoke it, and read it from an environment variable", null);
	}

	/**
	 * Folder expansion: every skill is scanned and its findings are prefixed with the
	 * skill name.
	 */
	@Test
	void folderScanAggregatesAndPrefixesSkills() throws IOException {
		secretSkill("alpha");
		secretSkill("beta");

		ValidationResult perSkill = new ValidationResult();
		perSkill.addStructuredFinding(secretFinding(), true);

		FakeEngine engine = new FakeEngine(perSkill);
		ValidationResult result = new SecretsValidator(engine).validate(tmp);

		assertEquals(2, engine.scanned.size());
		assertEquals(2, result.getFindings().size());
		assertTrue(result.getFindings()
			.stream()
			.allMatch(f -> f.getFilePath().startsWith("[") && f.getFilePath().contains("] script.py")));
		assertTrue(anyContains(result.getErrors(), "[alpha] [SECRET-HIGH]"));
		assertTrue(anyContains(result.getErrors(), "[beta] [SECRET-HIGH]"));
		assertTrue(anyContains(result.getMessages(), "Scanning for secrets in 2 skill(s)"));
		assertFalse(result.isPassed());
	}

	/** A folder with no manifests is scanned directly (the Python no-skills fallback). */
	@Test
	void folderWithoutSkillsFallsBackToFolderScan() throws IOException {
		Path folder = tmp.resolve("loose");
		Files.createDirectories(folder);
		Files.writeString(folder.resolve("notes.md"), "no manifest here\n");

		FakeEngine engine = new FakeEngine(new ValidationResult());
		ValidationResult result = new SecretsValidator(engine).validate(folder);

		assertEquals(List.of(folder), engine.scanned);
		assertTrue(result.isPassed());
	}

	/** The engine seam is what the validator scans with. */
	@Test
	void exposesInjectedEngine() {
		FakeEngine engine = new FakeEngine(new ValidationResult());
		assertEquals(engine, new SecretsValidator(engine).getScanner());
	}

	// ------------------------------------------------------- gitleaks driver

	/**
	 * {@code test_run_gitleaks_end_to_end}: exit 10 plus a JSON report yields shaped
	 * findings.
	 */
	@Test
	void driverEndToEndProducesStructuredFindings() throws IOException {
		FakeTool tool = new FakeTool();
		tool.enqueue(10, GITLEAKS_OUTPUT);

		ValidationResult result = new GitleaksSecretsScanner(tool).scan(secretSkill("e2e"));

		assertEquals(2, result.getFindings().size());
		Finding first = result.getFindings().get(0);
		assertEquals("generic-api-key", first.getCheckName());
		assertEquals("/tmp/test/config.py", first.getFilePath());
		assertEquals(5, first.getLineNumber().intValue());
		assertEquals("SECRET", first.getCategory());
		Finding second = result.getFindings().get(1);
		assertEquals("nvidia-ngc-api-key", second.getCheckName());
		assertEquals(8, second.getLineNumber().intValue());
		assertEquals("SECRET", second.getCategory());
		assertEquals("8.18.0", result.getScanners().get("gitleaks"));
		assertTrue(anyContains(result.getMessages(), "Gitleaks found 2 potential secret(s)"));

		String rendered = String.join("\n", result.getErrors()) + "\n" + String.join("\n", result.getWarnings()) + "\n"
				+ String.join("\n", result.getMessages());
		assertFalse(rendered.contains(AWS_KEY), "reported secret leaked into the result");
		assertFalse(rendered.contains(NVIDIA_KEY), "reported secret leaked into the result");
	}

	/** {@code test_unexpected_exit_does_not_echo_scanner_stdout}. */
	@Test
	void driverUnexpectedExitRedactsScannerOutput() throws IOException {
		FakeTool tool = new FakeTool();
		tool.enqueue(2, GITLEAKS_OUTPUT);

		ValidationResult result = new GitleaksSecretsScanner(tool).scan(secretSkill("redact"));

		String rendered = String.join("\n", result.getErrors()) + "\n" + String.join("\n", result.getWarnings()) + "\n"
				+ String.join("\n", result.getMessages());
		assertFalse(rendered.contains(AWS_KEY));
		assertFalse(rendered.contains(NVIDIA_KEY));
		assertTrue(rendered.toLowerCase().contains("redacted"));
		assertEquals(List.of("gitleaks"), result.getIncompleteScans());
	}

	/** {@code test_validate_clean_skill} at the driver level. */
	@Test
	void driverCleanScanPasses() throws IOException {
		FakeTool tool = new FakeTool();
		tool.enqueue(0, "");

		ValidationResult result = new GitleaksSecretsScanner(tool).scan(secretSkill("clean"));

		assertTrue(result.isPassed());
		assertTrue(anyContains(result.getMessages(), "No secrets detected"));
		assertTrue(result.getSuccessDetails().stream().anyMatch(d -> d.getCheckName().equals("gitleaks")));
	}

	/** An empty JSON array on a clean exit is still a clean scan. */
	@Test
	void driverCleanExitWithEmptyReportPasses() throws IOException {
		FakeTool tool = new FakeTool();
		tool.enqueue(0, "[]");

		ValidationResult result = new GitleaksSecretsScanner(tool).scan(secretSkill("clean-array"));

		assertTrue(result.isPassed());
		assertTrue(anyContains(result.getMessages(), "No secrets detected"));
	}

	/**
	 * {@code test_validate_skill_with_secrets}: the findings exit code is overridden to
	 * 10.
	 */
	@Test
	void driverInvokesGitleaksWithUpstreamArgv() throws IOException {
		Path skill = secretSkill("argv");
		FakeTool tool = new FakeTool();
		tool.enqueue(10, GITLEAKS_OUTPUT);

		new GitleaksSecretsScanner(tool).scan(skill);

		List<String> args = tool.lastArgs();
		assertEquals("detect", args.get(0));
		assertEquals("10", tool.argAfter("--exit-code"));
		assertEquals("json", tool.argAfter("--report-format"));
		assertEquals("-", tool.argAfter("--report-path"));
		assertEquals(".", tool.argAfter("--source"));
		assertTrue(args.contains("--no-git"));
		assertEquals(List.of(skill.toRealPath()), tool.recordedCwd);
		assertTrue(tool.configExisted.get(0), "config file must exist while gitleaks runs");
	}

	/** A direct file target runs from its parent with a "./" prefixed source. */
	@Test
	void driverScansLooseFileFromItsParent() throws IOException {
		Path file = tmp.resolve("-weird-name.py");
		Files.writeString(file, "x = 1\n");

		FakeTool tool = new FakeTool();
		tool.enqueue(0, "");
		new GitleaksSecretsScanner(tool).scan(file);

		assertEquals("./-weird-name.py", tool.argAfter("--source"));
		assertEquals(List.of(tmp.toRealPath()), tool.recordedCwd);
	}

	/** Git-history mode scans the absolute path and keeps git history. */
	@Test
	void driverGitHistoryUsesAbsolutePathAndKeepsGit() throws IOException {
		Path skill = secretSkill("history");
		FakeTool tool = new FakeTool();
		tool.enqueue(0, "");

		new GitleaksSecretsScanner(tool, true).scan(skill);

		assertEquals(skill.toRealPath().toString(), tool.argAfter("--source"));
		assertEquals(1, tool.recordedCwd.size());
		assertNull(tool.recordedCwd.get(0), "git-history mode runs in the inherited working directory");
		assertFalse(tool.lastArgs().contains("--no-git"));
	}

	/** {@code test_missing_scanner_is_incomplete} for gitleaks. */
	@Test
	void driverMissingBinaryWarnsAndMarksIncomplete() throws IOException {
		FakeTool tool = new FakeTool();
		tool.available = false;

		ValidationResult result = new GitleaksSecretsScanner(tool).scan(secretSkill("missing"));

		assertTrue(anyContains(result.getWarnings(), "gitleaks not installed - secrets scanning skipped."));
		assertTrue(anyContains(result.getWarnings(), "brew install gitleaks"));
		assertEquals(List.of("gitleaks"), result.getIncompleteScans());
		assertEquals("incomplete", result.getStatus());
		assertFalse(result.isPassed());
		assertTrue(tool.recordedArgs.isEmpty(), "an unavailable tool must not be invoked");
	}

	/** {@code test_gitleaks_timeout_marks_incomplete}. */
	@Test
	void driverTimeoutMarksIncomplete() throws IOException {
		FakeTool tool = new FakeTool();
		tool.enqueueError("Gitleaks timed out after 120 seconds");

		ValidationResult result = new GitleaksSecretsScanner(tool).scan(secretSkill("timeout"));

		assertEquals(List.of("gitleaks"), result.getIncompleteScans());
		assertEquals("incomplete", result.getStatus());
		assertFalse(result.isPassed());
		assertTrue(anyContains(result.getWarnings(), "timed out"));
	}

	/** A tool error naming an install problem also carries the hint. */
	@Test
	void driverAppendsInstallHintToCouldnStartErrors() throws IOException {
		FakeTool tool = new FakeTool();
		tool.enqueueError("couldn't execute gitleaks");

		ValidationResult result = new GitleaksSecretsScanner(tool).scan(secretSkill("cant-start"));

		assertTrue(anyContains(result.getWarnings(), "brew install gitleaks"));
		assertEquals(List.of("gitleaks"), result.getIncompleteScans());
	}

	/** A plain tool error is reported without a trailing separator. */
	@Test
	void driverStripsTrailingSeparatorFromToolErrors() throws IOException {
		FakeTool tool = new FakeTool();
		tool.enqueueError("Gitleaks execution failed: boom.");

		ValidationResult result = new GitleaksSecretsScanner(tool).scan(secretSkill("plain-error"));

		assertTrue(anyContains(result.getWarnings(), "Gitleaks execution failed: boom"));
		assertFalse(anyContains(result.getWarnings(), "boom. ."));
	}

	/** {@code test_gitleaks_malformed_findings_report_is_incomplete}. */
	@Test
	void driverMalformedFindingsReportIsIncomplete() throws IOException {
		FakeTool tool = new FakeTool();
		tool.enqueue(10, "not-json");

		ValidationResult result = new GitleaksSecretsScanner(tool).scan(secretSkill("malformed"));

		assertEquals(List.of("gitleaks"), result.getIncompleteScans());
		assertEquals("incomplete", result.getStatus());
		assertFalse(anyContains(result.getMessages(), "No secrets detected"));
	}

	/** Findings arriving on a clean exit mean the scanner state cannot be trusted. */
	@Test
	void driverFindingsOnCleanExitIsInconsistent() throws IOException {
		FakeTool tool = new FakeTool();
		tool.enqueue(0, GITLEAKS_OUTPUT);

		ValidationResult result = new GitleaksSecretsScanner(tool).scan(secretSkill("inconsistent"));

		assertTrue(anyContains(result.getErrors(),
				"Gitleaks returned findings with clean exit code 0; scanner status is inconsistent"));
		assertEquals(List.of("gitleaks"), result.getIncompleteScans());
		// Partial findings stay available even though the scan cannot be trusted.
		assertEquals(2, result.getFindings().size());
	}

	/**
	 * A clean exit that returns something other than a valid empty JSON report is
	 * incomplete.
	 */
	@Test
	void driverCleanExitWithNonJsonReportIsIncomplete() throws IOException {
		FakeTool tool = new FakeTool();
		tool.enqueue(0, "not json at all");

		ValidationResult result = new GitleaksSecretsScanner(tool).scan(secretSkill("bad-clean"));

		assertTrue(anyContains(result.getErrors(), "Gitleaks clean exit did not return a valid empty JSON report"));
		assertEquals(List.of("gitleaks"), result.getIncompleteScans());
	}

	/** An exit-10 run that reports no findings is not a completed scan. */
	@Test
	void driverFindingsExitWithEmptyReportIsIncomplete() throws IOException {
		FakeTool tool = new FakeTool();
		tool.enqueue(10, "[]");

		ValidationResult result = new GitleaksSecretsScanner(tool).scan(secretSkill("empty-findings"));

		assertTrue(
				anyContains(result.getErrors(), "Gitleaks findings exit did not return a valid nonempty JSON report"));
		assertEquals(List.of("gitleaks"), result.getIncompleteScans());
	}

	/** Crash stderr is truncated to 200 characters, matching the Python slice. */
	@Test
	void driverTruncatesCrashStderr() throws IOException {
		FakeTool tool = new FakeTool();
		tool.scripted.add(new SecretsScannerTool.RunResult(true, "", "x".repeat(400), 2, null));

		ValidationResult result = new GitleaksSecretsScanner(tool).scan(secretSkill("crash"));

		String error = result.getErrors().get(0);
		assertTrue(error.contains("x".repeat(200)));
		assertFalse(error.contains("x".repeat(201)));
		assertTrue(error.startsWith("Gitleaks error (exit code 2): "));
	}

	/** The temp config is written for the scan and cleaned up afterwards. */
	@Test
	void driverCleansUpTempConfig() throws IOException {
		FakeTool tool = new FakeTool();
		tool.enqueue(0, "");

		new GitleaksSecretsScanner(tool).scan(secretSkill("cleanup"));

		String path = tool.argAfter("--config");
		assertTrue(path.endsWith(".toml"));
		assertFalse(Files.exists(Path.of(path)), "temp config left behind at " + path);
	}

}
