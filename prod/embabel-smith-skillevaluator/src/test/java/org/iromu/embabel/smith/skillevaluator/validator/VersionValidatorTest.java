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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.iromu.embabel.smith.skillevaluator.model.Finding;
import org.iromu.embabel.smith.skillevaluator.model.SuccessDetail;
import org.iromu.embabel.smith.skillevaluator.model.ValidationResult;

/**
 * Ports of the cases from {@code tests/validators/test_version.py}: strict semver
 * acceptance, the untrusted/unbounded rejections, and previous-version bump enforcement.
 *
 * <p>
 * The Python suite exercises the {@code SKILLEVALUATOR_PREVIOUS_VERSION} fallback with
 * {@code monkeypatch.setenv}. A JVM test cannot change its own environment, so the same
 * contract is covered through the constructor injection seam (explicit value wins,
 * {@code null} falls back to {@link VersionValidator#PREVIOUS_VERSION_ENV}).
 */
class VersionValidatorTest {

	@TempDir
	Path tmp;

	/**
	 * Write {@code tmp/versioned-skill/SKILL.md} the way the Python {@code _write_skill}
	 * helper does: an optional top-level {@code version} line plus a {@code metadata}
	 * block that may carry {@code version}.
	 */
	private Path writeSkill(String versionLine, String topLevelVersion) throws IOException {
		Path skillDir = tmp.resolve("versioned-skill");
		Files.createDirectories(skillDir);
		String top = topLevelVersion.isEmpty() ? "" : "version: " + topLevelVersion + "\n";
		String metadata = "metadata:\n  author: Test User <testuser@example.com>\n";
		if (!versionLine.isEmpty()) {
			metadata += "  version: " + versionLine + "\n";
		}
		Files.writeString(skillDir.resolve("SKILL.md"),
				"---\nname: versioned-skill\n"
						+ "description: A skill for testing optional semantic version validation.\n" + top + metadata
						+ "---\n\n# Versioned Skill\n\nUse this skill to exercise version validation.\n");
		return skillDir;
	}

	private Path writeSkill(String versionLine) throws IOException {
		return writeSkill(versionLine, "");
	}

	private static Set<String> checkNames(ValidationResult result) {
		return result.getFindings().stream().map(Finding::getCheckName).collect(Collectors.toSet());
	}

	private static SuccessDetail detail(ValidationResult result, String checkName) {
		return result.getSuccessDetails()
			.stream()
			.filter(entry -> entry.getCheckName().equals(checkName))
			.findFirst()
			.orElseThrow();
	}

	// ------------------------------------------------------------------
	// Optional label
	// ------------------------------------------------------------------

	@Test
	void missingVersionIsValidCommitHashOnlyHistory() throws IOException {
		ValidationResult result = new VersionValidator().validate(writeSkill(""));

		assertTrue(result.isPassed());
		assertEquals(Set.of(), checkNames(result));
		assertNotNullDetail(result, "version_optional");
	}

	@Test
	void topLevelVersionIsIgnoredWhenMetadataVersionMissing() throws IOException {
		ValidationResult result = new VersionValidator().validate(writeSkill("", "\"1.0\""));

		assertTrue(result.isPassed());
		assertEquals(Set.of(), checkNames(result));
		assertNotNullDetail(result, "version_optional");
	}

	@Test
	void topLevelVersionIsShadowedByMetadataVersion() throws IOException {
		ValidationResult result = new VersionValidator("1.1.9").validate(writeSkill("1.2.0", "\"9.9.9\""));

		assertTrue(result.isPassed());
		assertEquals(Set.of(), checkNames(result));
		assertEquals("1.2.0", detail(result, "version_semver").getMetadata().get("version"));
	}

	private static void assertNotNullDetail(ValidationResult result, String checkName) {
		SuccessDetail detail = detail(result, checkName);
		assertTrue(detail.getMessage() != null && !detail.getMessage().isEmpty());
	}

	// ------------------------------------------------------------------
	// Strict semver
	// ------------------------------------------------------------------

	@ParameterizedTest
	@ValueSource(strings = { "1.2", "1.2.3-alpha", "1.2.3+build", "v1.2.3" })
	void malformedVersionIsRejectedWhenPresent(String version) throws IOException {
		ValidationResult result = new VersionValidator().validate(writeSkill(version));

		assertFalse(result.isPassed(), version + " should be rejected");
		assertTrue(checkNames(result).contains("version_semver"));
	}

	static Stream<String> untrustedOrUnboundedVersions() {
		return Stream.of("01.2.3", "1.02.3", "1.2.03", "\uff11.2.3", // fullwidth digit
																		// one: re.ASCII
																		// keeps [0-9]
																		// ASCII-only
				"9".repeat(5000) + ".2.3");
	}

	@ParameterizedTest
	@MethodSource("untrustedOrUnboundedVersions")
	void untrustedOrUnboundedVersionIsRejectedWithoutCrashing(String version) throws IOException {
		ValidationResult result = new VersionValidator("1.2.0").validate(writeSkill("\"" + version + "\""));

		assertFalse(result.isPassed(), version + " should be rejected");
		assertTrue(checkNames(result).contains("version_semver"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "0", "false", "[]", "{}" })
	void explicitFalseyNonStringVersionIsRejected(String version) throws IOException {
		ValidationResult result = new VersionValidator("1.2.0").validate(writeSkill(version));

		assertFalse(result.isPassed(), version + " should be rejected");
		assertTrue(checkNames(result).contains("version_semver"));
	}

	@Test
	void unquotedOnePointZeroStringifiesWithoutAPatchComponent() throws IOException {
		// YAML parses an unquoted 1.0 as a number, so str() yields "1.0" rather than
		// "1.0.0".
		ValidationResult result = new VersionValidator().validate(writeSkill("1.0"));

		assertFalse(result.isPassed());
		assertTrue(checkNames(result).contains("version_semver"));
	}

	@Test
	void boundedAsciiVersionIsAccepted() throws IOException {
		ValidationResult result = new VersionValidator().validate(writeSkill("1.2.3"));

		assertTrue(result.isPassed());
		assertEquals(Set.of(), checkNames(result));
		assertEquals("1.2.3", detail(result, "version_semver").getMetadata().get("version"));
	}

	// ------------------------------------------------------------------
	// Previous-version enforcement
	// ------------------------------------------------------------------

	@Test
	void unboundedPreviousVersionIsRejectedWithoutCrashing() throws IOException {
		ValidationResult result = new VersionValidator("9".repeat(5000) + ".2.3").validate(writeSkill("\"1.2.3\""));

		assertFalse(result.isPassed());
		assertTrue(checkNames(result).contains("previous_version_semver"));
	}

	@Test
	void equalVersionLabelIsRejected() throws IOException {
		ValidationResult result = new VersionValidator("1.2.0").validate(writeSkill("1.2.0"));

		assertFalse(result.isPassed());
		assertTrue(checkNames(result).contains("version_monotonic"));
	}

	@Test
	void versionBumpIsAllowed() throws IOException {
		ValidationResult result = new VersionValidator("1.2.0").validate(writeSkill("1.2.1"));

		assertTrue(result.isPassed());
		assertEquals(Set.of(), checkNames(result));
	}

	@Test
	void versionDowngradeIsRejected() throws IOException {
		ValidationResult result = new VersionValidator("1.2.0").validate(writeSkill("1.1.9"));

		assertFalse(result.isPassed());
		assertTrue(checkNames(result).contains("version_monotonic"));
	}

	@Test
	void malformedPreviousVersionIsRejected() throws IOException {
		ValidationResult result = new VersionValidator("1.2").validate(writeSkill("1.2.0"));

		assertFalse(result.isPassed());
		assertTrue(checkNames(result).contains("previous_version_semver"));
	}

	@Test
	void removingVersionLabelIsRejectedWithPreviousVersion() throws IOException {
		ValidationResult result = new VersionValidator("1.2.0").validate(writeSkill(""));

		assertFalse(result.isPassed());
		assertEquals(Set.of("version_missing"), checkNames(result));
	}

	@Test
	void missingVersionReportsMalformedPreviousBound() throws IOException {
		ValidationResult result = new VersionValidator("1.2").validate(writeSkill(""));

		assertFalse(result.isPassed());
		assertEquals(Set.of("previous_version_semver"), checkNames(result));
	}

	// ------------------------------------------------------------------
	// Environment-variable contract (constructor-injection seam only)
	// ------------------------------------------------------------------

	@Test
	void explicitPreviousVersionOverridesTheEnvironmentFallback() {
		// Python: previous_version or os.getenv(SKILLEVALUATOR_PREVIOUS_VERSION) — an
		// explicit
		// value is kept, and getPreviousVersion() exposes whichever bound was chosen.
		assertEquals("1.2.0", new VersionValidator("1.2.0").getPreviousVersion());
	}

	@Test
	void nullPreviousVersionFallsBackToTheEnvironment() throws IOException {
		Assumptions.assumeTrue(System.getenv(VersionValidator.PREVIOUS_VERSION_ENV) == null,
				VersionValidator.PREVIOUS_VERSION_ENV + " is set in this environment");

		// No bound from the constructor and none from the environment: the monotonic
		// check skips.
		ValidationResult result = new VersionValidator(null).validate(writeSkill("1.2.1"));

		assertTrue(result.isPassed());
		assertFalse(checkNames(result).contains("version_monotonic"));
		assertFalse(checkNames(result).contains("previous_version_semver"));
	}

	// ------------------------------------------------------------------
	// Folder scope
	// ------------------------------------------------------------------

	@Test
	void folderWithoutSkillsIsAnError() throws IOException {
		Path folder = tmp.resolve("empty-folder");
		Files.createDirectories(folder);

		ValidationResult result = new VersionValidator().validate(folder);

		assertFalse(result.isPassed());
		assertTrue(String.join(" ", result.getErrors()).contains("No skills found"));
	}

	@Test
	void folderValidatesEverySkill() throws IOException {
		Path folder = tmp.resolve("skills");
		for (String name : List.of("skill-a", "skill-b")) {
			Files.createDirectories(folder.resolve(name));
			Files.writeString(folder.resolve(name + "/SKILL.md"), "---\nname: " + name
					+ "\ndescription: A skill.\nmetadata:\n  version: 1.0.0\n---\n\n# " + name + "\n");
		}

		ValidationResult result = new VersionValidator().validate(folder);

		assertTrue(result.isPassed());
		// A passing skill's own checks are folded into one success detail named after the
		// skill.
		Set<String> detailNames = result.getSuccessDetails()
			.stream()
			.map(SuccessDetail::getCheckName)
			.collect(Collectors.toSet());
		assertTrue(detailNames.containsAll(Set.of("skill-a", "skill-b")), detailNames.toString());
	}

}
