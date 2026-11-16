/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.iromu.embabel.smith.skillevaluator.model.Finding;
import org.iromu.embabel.smith.skillevaluator.model.Severity;
import org.iromu.embabel.smith.skillevaluator.model.ValidationResult;

/**
 * Unit tests for the Gitleaks report->Finding conversion and the generated config, ported
 * from the secrets expectations in {@code tests/validators/test_secrets_structured.py},
 * {@code tests/validators/test_security_validators.py::TestSecretsValidator::test_severity_mapping}
 * and {@code tests/validators/test_artifact_dir_exclusion.py}
 * ({@code TestGitleaksArtifactExclusion}, {@code TestGitleaksTestPathAllowlist}).
 *
 * <p>
 * These live in the scanner's own package because the conversion mirrors Python private
 * methods and stays package-private here; the validator-facing behaviour is covered by
 * {@code SecretsValidatorTest}.
 */
class GitleaksSecretsScannerTest {

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

	private static boolean anyContains(List<String> haystack, String needle) {
		return haystack.stream().anyMatch(item -> item.contains(needle));
	}

	// ---------------------------------------------------------------- conversion

	/** The JSON->Finding conversion, driven directly from the captured report fixture. */
	@Test
	void convertsGitleaksReportEntries() {
		JsonNode findings = GitleaksSecretsScanner.parseJsonOutput(GITLEAKS_OUTPUT);
		assertNotNull(findings);

		Finding first = GitleaksSecretsScanner.convertFinding(findings.get(0));
		assertEquals("SECRET", first.getCategory());
		assertEquals("generic-api-key", first.getCheckName());
		assertEquals(Severity.CRITICAL, first.getSeverity(), "tags [key, api] match the \"key\" keyword first");
		assertEquals("Generic API Key in /tmp/test/config.py:5 [generic-api-key]", first.getMessage());
		assertEquals("/tmp/test/config.py", first.getFilePath());
		assertEquals(5, first.getLineNumber().intValue());
		assertEquals("Remove the secret from /tmp/test/config.py, revoke it, and read it from an environment variable",
				first.getSuggestion());
		assertFalse(first.getMessage().contains(AWS_KEY));
		assertFalse(first.toMap().toString().contains(AWS_KEY));

		Finding second = GitleaksSecretsScanner.convertFinding(findings.get(1));
		assertEquals("nvidia-ngc-api-key", second.getCheckName());
		assertEquals("NVIDIA NGC API Key in /tmp/test/config.py:8 [nvidia-ngc-api-key]", second.getMessage());
		assertEquals(8, second.getLineNumber().intValue());
	}

	/**
	 * Missing keys take the Python defaults; a report entry never carries the secret
	 * value onward.
	 */
	@Test
	void convertsSparseReportEntryWithDefaults() {
		JsonNode entry = GitleaksSecretsScanner.parseJsonOutput("[{\"RuleID\": \"weird\"}]");
		Finding finding = GitleaksSecretsScanner.convertFinding(entry.get(0));

		assertEquals("weird", finding.getCheckName());
		assertEquals("Secret detected in unknown:0 [weird]", finding.getMessage());
		assertEquals("unknown", finding.getFilePath());
		assertEquals(0, finding.getLineNumber().intValue());
		assertEquals(Severity.HIGH, finding.getSeverity());
	}

	/**
	 * {@code _process_findings} adds the count message and fails the run on error
	 * severities.
	 */
	@Test
	void processFindingsTalliesAndFails() {
		JsonNode findings = GitleaksSecretsScanner.parseJsonOutput(GITLEAKS_OUTPUT);
		ValidationResult result = new ValidationResult();

		GitleaksSecretsScanner.processFindings(findings, result);

		assertTrue(anyContains(result.getMessages(), "Gitleaks found 2 potential secret(s)"));
		assertEquals(2, result.getFindings().size());
		assertFalse(result.isPassed());
		assertEquals(2, result.getErrors().size());
	}

	/** A non-object report entry or unparsable output is not a findings list. */
	@Test
	void parseJsonOutputMirrorsPythonSemantics() {
		assertEquals(null, GitleaksSecretsScanner.parseJsonOutput(""));
		assertEquals(null, GitleaksSecretsScanner.parseJsonOutput("   "));
		assertEquals(null, GitleaksSecretsScanner.parseJsonOutput("not json"));
		assertTrue(GitleaksSecretsScanner.parseJsonOutput("[]").isArray());
		assertTrue(GitleaksSecretsScanner.parseJsonOutput("{}").isObject());
	}

	// ------------------------------------------------------------------ severity

	/** {@code test_severity_mapping}. */
	@Test
	void severityFromTagsThenRuleId() {
		assertEquals(Severity.CRITICAL, GitleaksSecretsScanner.determineSeverity("aws-key", List.of("aws", "key")));
		assertEquals(Severity.CRITICAL,
				GitleaksSecretsScanner.determineSeverity("private-key", List.of("private-key")));
		assertEquals(Severity.HIGH, GitleaksSecretsScanner.determineSeverity("generic-api-key", List.of("api")));
		assertEquals(Severity.HIGH, GitleaksSecretsScanner.determineSeverity("unknown-rule", List.of()));
	}

	/** Tag order decides when several tags match: the first matching tag wins. */
	@Test
	void severityIsTagOrderSensitive() {
		assertEquals(Severity.CRITICAL,
				GitleaksSecretsScanner.determineSeverity("generic-api-key", List.of("key", "api")));
		assertEquals(Severity.HIGH, GitleaksSecretsScanner.determineSeverity("generic-api-key", List.of("api", "key")));
		assertEquals(Severity.MEDIUM, GitleaksSecretsScanner.determineSeverity("opaque", List.of("GENERIC")));
		assertEquals(Severity.CRITICAL, GitleaksSecretsScanner.determineSeverity("pw", List.of("password")));
		assertEquals(Severity.CRITICAL, GitleaksSecretsScanner.determineSeverity("x", List.of("secret")));
		assertEquals(Severity.CRITICAL, GitleaksSecretsScanner.determineSeverity("x", List.of("token")));
		assertEquals(Severity.CRITICAL, GitleaksSecretsScanner.determineSeverity("x", List.of("credential")));
		assertEquals(Severity.HIGH, GitleaksSecretsScanner.determineSeverity("basic-auth", List.of("auth")));
	}

	/**
	 * With no tags the rule id carries the severity — and "key" outranks "api" there too.
	 */
	@Test
	void severityFallsBackToRuleId() {
		assertEquals(Severity.CRITICAL, GitleaksSecretsScanner.determineSeverity("AWS-KEY", List.of()));
		assertEquals(Severity.HIGH, GitleaksSecretsScanner.determineSeverity("auth-flow", List.of()));
		assertEquals(Severity.HIGH, GitleaksSecretsScanner.determineSeverity("opaque", List.of()));
		assertEquals(Severity.MEDIUM, GitleaksSecretsScanner.determineSeverity("generic-api-key", List.of("generic")));
		assertEquals(Severity.CRITICAL, GitleaksSecretsScanner.determineSeverity("generic-api-key", List.of()));
	}

	// -------------------------------------------------------------------- config

	/**
	 * {@code TestGitleaksArtifactExclusion}: every Tier 1 artifact dir is allowlisted.
	 */
	@Test
	void configAllowlistsArtifactDirs() {
		List<String> paths = allowlistPaths(GitleaksSecretsScanner.buildConfig());

		for (String name : List.of("evals", ".evals", "results", ".results", "versions", ".versions", "__pycache__",
				".git", ".venv", "node_modules")) {
			assertTrue(paths.contains("(^|/)" + escapeRegex(name) + "(/|$)"), "missing allowlist entry for " + name);
		}
		assertTrue(GitleaksSecretsScanner.buildConfig().contains("useDefault = true"),
				"config must extend the defaults");
	}

	/**
	 * {@code TestGitleaksTestPathAllowlist}: test dirs are path components, not
	 * substrings.
	 */
	@Test
	void configSkipsTestDirectoriesNotSubstringNames() {
		List<String> paths = allowlistPaths(GitleaksSecretsScanner.buildConfig());

		assertTrue(paths.contains("(^|/)(tests?|examples?|fixtures?|mocks?)(/|$)"));
		assertFalse(paths.contains("(.*)?test(.*)?"));

		List<Pattern> compiled = paths.stream().map(Pattern::compile).toList();
		assertTrue(skipped(compiled, "tests/helper.py"));
		assertTrue(skipped(compiled, "test/helper.py"));
		assertTrue(skipped(compiled, "examples/sample.json"));
		assertTrue(skipped(compiled, "fixtures/data.json"));
		assertTrue(skipped(compiled, "mocks/client.py"));
		assertFalse(skipped(compiled, "scripts/latest.py"));
		assertFalse(skipped(compiled, "attestation.py"));
		assertFalse(skipped(compiled, "skills/data-latest/prod.py"));
		assertFalse(skipped(compiled, "scripts/prod.py"));
		assertFalse(skipped(compiled, "contest/score.py"));
	}

	/** The NVIDIA rules ship with the config and use the upstream regexes. */
	@Test
	void configCarriesNvidiaRules() {
		String config = GitleaksSecretsScanner.buildConfig();

		assertTrue(config.contains("id = \"nvidia-ngc-api-key\""));
		assertTrue(config.contains("regex = '''\\$oauthtoken:\\S{84}'''"));
		assertTrue(config.contains("id = \"nvidia-api-key\""));
		assertTrue(config.contains("regex = '''nvapi-[A-Za-z0-9_-]{40,}'''"));
	}

	/** Placeholder credentials are allowlisted so docs do not trip the scanner. */
	@Test
	void configAllowlistsPlaceholderRegexes() {
		String config = GitleaksSecretsScanner.buildConfig();

		for (String regex : List.of("YOUR_.*_HERE", "INSERT_.*_HERE", "PLACEHOLDER", "example\\.com",
				"test\\.nvidia\\.com", "dummy", "fake")) {
			assertTrue(config.contains("'''" + regex + "'''"), "missing allowlist regex " + regex);
		}
	}

	/**
	 * The NVIDIA rules come after the allowlist, matching the Python concatenation order.
	 */
	@Test
	void configPutsRulesAfterTheAllowlist() {
		String config = GitleaksSecretsScanner.buildConfig();
		assertTrue(config.indexOf("[[rules]]") > config.indexOf("[allowlist]"));
	}

	/** {@code re.escape} parity: only regex-special characters get a backslash. */
	@Test
	void pythonReEscapeMatchesUpstream() {
		assertEquals("plain_name1", GitleaksSecretsScanner.pythonReEscape("plain_name1"));
		assertEquals("\\.git", GitleaksSecretsScanner.pythonReEscape(".git"));
		assertEquals("a\\+b\\(c\\)\\[d\\]", GitleaksSecretsScanner.pythonReEscape("a+b(c)[d]"));
		assertEquals("a\\|b\\*c\\?d", GitleaksSecretsScanner.pythonReEscape("a|b*c?d"));
		assertEquals("a\\ b", GitleaksSecretsScanner.pythonReEscape("a b"));
	}

	/** Python's {@code rstrip(". ")} on the composed warning text. */
	@Test
	void rstripDotSpaceMatchesPython() {
		assertEquals("abc", GitleaksSecretsScanner.rstripDotSpace("abc. . "));
		assertEquals("", GitleaksSecretsScanner.rstripDotSpace(". ."));
		assertEquals("a.b", GitleaksSecretsScanner.rstripDotSpace("a.b"));
	}

	// ------------------------------------------------------------------ helpers

	/** Reads the {@code paths = [...]} literal strings out of the generated TOML. */
	private static List<String> allowlistPaths(String config) {
		List<String> paths = new java.util.ArrayList<>();
		boolean inPaths = false;
		for (String line : config.split("\n", -1)) {
			if (line.equals("paths = [")) {
				inPaths = true;
			}
			else if (inPaths && line.equals("]")) {
				break;
			}
			else if (inPaths && line.startsWith("    '''")) {
				String entry = line.trim();
				// TOML literal (''') strings keep backslashes verbatim, as does tomllib
				// upstream.
				paths.add(entry.substring(3, entry.lastIndexOf("'''")));
			}
		}
		return paths;
	}

	private static boolean skipped(List<Pattern> patterns, String relativePath) {
		return patterns.stream().anyMatch(pattern -> pattern.matcher(relativePath).find());
	}

	/** Mirrors the Python test's {@code re.escape(name)} inside the expected pattern. */
	private static String escapeRegex(String name) {
		return name.replace(".", "\\.");
	}

}
