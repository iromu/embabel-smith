/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.iromu.embabel.smith.skillevaluator.model.ValidationResult;
import org.iromu.embabel.smith.skillevaluator.spi.SecretsScanner;
import org.iromu.embabel.smith.skillevaluator.spi.SecurityScanner;

/**
 * End-to-end pipeline tests over the vendored upstream reference skills (Apache-2.0,
 * tier3/reference_skills) and synthetic bad skills.
 */
class Tier1RunnerIntegrationTest {

	/**
	 * Engine that always "ran" and found nothing, so deterministic checks decide the
	 * outcome.
	 */
	private static final SecurityScanner CLEAN_SECURITY = new SecurityScanner() {
		@Override
		public String getName() {
			return "test-security";
		}

		@Override
		public boolean isAvailable() {
			return true;
		}

		@Override
		public ValidationResult scanSecurity(Path skillPath) {
			ValidationResult r = new ValidationResult();
			r.recordScanner(getName(), "test");
			return r;
		}
	};

	private static final SecretsScanner CLEAN_SECRETS = new SecretsScanner() {
		@Override
		public String getName() {
			return "test-secrets";
		}

		@Override
		public boolean isAvailable() {
			return true;
		}

		@Override
		public ValidationResult scan(Path skillPath) {
			ValidationResult r = new ValidationResult();
			r.recordScanner(getName(), "test");
			return r;
		}
	};

	@TempDir
	Path tempDir;

	private static Path fixturesRoot;

	@BeforeAll
	static void locateFixtures() {
		fixturesRoot = Path.of("src/test/resources/fixtures").toAbsolutePath();
		if (!Files.isDirectory(fixturesRoot)) {
			// surefire runs from project root; keep the message actionable
			throw new IllegalStateException("fixture directory missing: " + fixturesRoot);
		}
	}

	private Path copyFixture(String name) throws Exception {
		Path target = tempDir.resolve(name);
		try (var walk = Files.walk(fixturesRoot.resolve(name))) {
			for (Path src : walk.toList()) {
				Path dst = target.resolve(fixturesRoot.resolve(name).relativize(src).toString());
				if (Files.isDirectory(src)) {
					Files.createDirectories(dst);
				}
				else {
					Files.createDirectories(dst.getParent());
					Files.copy(src, dst);
				}
			}
		}
		return target;
	}

	private Tier1Runner.Builder cleanRun() {
		return Tier1Runner.builder().securityScanner(CLEAN_SECURITY).secretsScanner(CLEAN_SECRETS);
	}

	@Test
	void referenceCalculatorPassesDeterministicChecks() throws Exception {
		Path skill = copyFixture("calculator");
		List<ValidationResult> results = new Tier1Runner().run(skill, cleanRun().build());

		List<String> names = results.stream().map(ValidationResult::getValidatorName).toList();
		assertEquals(List.of("Schema & Repository Governance", "Semantic Version Validation", "Security Scan",
				"PII Scan", "License Compliance", "Code Risk Analysis", "Secrets Detection", "Code Integrity & Hygiene",
				"Unicode Smuggling Detection",
				// upstream stamps these two results with their short validator names
				"QUALITY", "SCRIPT_LINT"), names, "canonical pipeline order");

		byName(results, "Schema & Repository Governance")
			.ifPresent(r -> assertTrue(r.isPassed(), String.join("\n", r.getErrors())));
		byName(results, "License Compliance")
			.ifPresent(r -> assertTrue(r.isPassed(), String.join("\n", r.getErrors())));
		byName(results, "Unicode Smuggling Detection").ifPresent(r -> assertTrue(r.isPassed()));
		ValidationResult quality = byName(results, "QUALITY").orElseThrow();
		assertTrue(quality.isPassed(), "quality gate: " + String.join("\n", quality.getErrors()));
	}

	@Test
	void badSkillProducesSchemaAndPiiFindings() throws Exception {
		Path skill = tempDir.resolve("bad-skill");
		Files.createDirectories(skill);
		Files.writeString(skill.resolve("SKILL.md"), """
				---
				name: Bad_Skill
				description: "x"
				---

				Contact john.doe@acmecorp-consulting.com or call 555-123-4567 for help.
				""");

		List<ValidationResult> results = new Tier1Runner().run(skill, cleanRun().build());
		ValidationResult schema = byName(results, "Schema & Repository Governance").orElseThrow();
		assertFalse(schema.isPassed());
		ValidationResult pii = byName(results, "PII Scan").orElseThrow();
		// upstream deliberately exempts RFC 2606 placeholder domains, so use a
		// real-looking one
		assertFalse(pii.isPassed(), "non-placeholder email should raise PII findings");
		assertTrue(pii.getFindings().stream().anyMatch(f -> f.getCategory().equals("PII")));
	}

	@Test
	void skipRecordsSkippedResults() throws Exception {
		Path skill = copyFixture("calculator");
		List<ValidationResult> results = new Tier1Runner().run(skill, cleanRun().skip("secrets").build());
		ValidationResult secrets = byName(results, "Secrets Detection").orElseThrow();
		assertEquals(Boolean.TRUE, secrets.getMetadata().get("skipped"));
		assertEquals(Boolean.TRUE, secrets.getMetadata().get("skipped_by_user"));
	}

	@Test
	void unknownCheckFailsOptionValidation() throws Exception {
		Path skill = copyFixture("calculator");
		List<ValidationResult> results = new Tier1Runner().run(skill,
				Tier1Runner.builder().checks("schema,telepathy").build());
		ValidationResult first = results.get(0);
		assertEquals("Tier 1 option validation", first.getValidatorName());
		assertTrue(first.getErrors().get(0).contains("telepathy"));
	}

	@Test
	void failFastStopsAfterFirstFailingCheck() throws Exception {
		Path skill = tempDir.resolve("broken");
		Files.createDirectories(skill);
		Files.writeString(skill.resolve("SKILL.md"), "no frontmatter at all\n");
		List<ValidationResult> results = new Tier1Runner().run(skill,
				cleanRun().checks("schema,version").failFast(true).build());
		assertTrue(results.stream().anyMatch(r -> !r.isPassed()));
		assertTrue(results.size() <= 3, "fail-fast should stop before the whole lineup, got " + results.size());
	}

	@Test
	void missingScannerMarksScanIncomplete() throws Exception {
		Path skill = copyFixture("calculator");
		SecurityScanner missing = new SecurityScanner() {
			@Override
			public String getName() {
				return "ghost";
			}

			@Override
			public boolean isAvailable() {
				return false;
			}

			@Override
			public ValidationResult scanSecurity(Path skillPath) {
				ValidationResult r = new ValidationResult();
				r.addWarning("ghost not installed");
				r.markScanIncomplete("ghost");
				return r;
			}
		};
		List<ValidationResult> results = new Tier1Runner().run(skill,
				Tier1Runner.builder().securityScanner(missing).secretsScanner(CLEAN_SECRETS).build());
		ValidationResult security = byName(results, "Security Scan").orElseThrow();
		assertEquals("incomplete", security.getStatus());
		assertFalse(security.isPassed());
	}

	@Test
	void folderRunPrefixesFindingsPerSkill() throws Exception {
		Path folder = tempDir.resolve("collection");
		Files.createDirectories(folder);
		for (String name : List.of("skill-a", "skill-b")) {
			Path skill = folder.resolve(name);
			Files.createDirectories(skill);
			Files.writeString(skill.resolve("SKILL.md"), """
					---
					name: %s
					description: Use when testing folder scans with a placeholder description long enough to be fine.
					---

					Reach admin@corp.internal for access.
					""".formatted(name));
		}
		List<ValidationResult> results = new Tier1Runner().run(folder, cleanRun().checks("pii").build());
		ValidationResult pii = byName(results, "PII Scan").orElseThrow();
		assertFalse(pii.isPassed());
		assertTrue(pii.getFindings().stream().allMatch(f -> f.getFilePath().startsWith("[skill-")),
				"folder findings carry [skill] prefixes");
	}

	@Test
	void qualityGateUsesMinScore() throws Exception {
		Path skill = tempDir.resolve("thin");
		Files.createDirectories(skill);
		Files.writeString(skill.resolve("SKILL.md"), """
				---
				name: thin-skill
				description: Thin
				---

				Do the thing.
				""");
		ValidationResult quality = byName(
				new Tier1Runner().run(skill, cleanRun().checks("quality").minScore(95).build()), "QUALITY")
			.orElseThrow();
		assertFalse(quality.isPassed());
		assertNotNull(quality.getMetadata().get("gate_failures"));
	}

	private static java.util.Optional<ValidationResult> byName(List<ValidationResult> results, String name) {
		return results.stream().filter(r -> name.equals(r.getValidatorName())).findFirst();
	}

}
