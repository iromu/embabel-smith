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
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.iromu.embabel.smith.skillevaluator.model.SuccessDetail;
import org.iromu.embabel.smith.skillevaluator.model.ValidationResult;

/** Ports of the key cases from tests/validators/test_hygiene.py. */
class HygieneValidatorTest {

	private final HygieneValidator validator = new HygieneValidator();

	@TempDir
	Path tmp;

	private static SuccessDetail discoveryDetail(ValidationResult result) {
		return result.getSuccessDetails()
			.stream()
			.filter(d -> d.getCheckName().equals("test_discovery"))
			.findFirst()
			.orElseThrow();
	}

	private Path skillDir(String name) throws IOException {
		Path skill = tmp.resolve(name);
		Files.createDirectories(skill);
		return skill;
	}

	private void writeSkillManifest(Path skill) throws IOException {
		Files.writeString(skill.resolve("SKILL.md"), "---\nname: skill\ndescription: test\n---\n");
	}

	// ------------------------------------------------------------------
	// Test discovery (static only, never executes)
	// ------------------------------------------------------------------

	@ParameterizedTest
	@MethodSource("payloadPaths")
	void hygieneNeverExecutesTargetPayloadModules(String payloadPath) throws IOException {
		Path skill = skillDir("skill-exec-" + payloadPath.replace('/', '_'));
		Path testsDir = skill.resolve("tests");
		Files.createDirectories(testsDir);
		writeSkillManifest(skill);
		Files.writeString(testsDir.resolve("test_present.py"), "def test_present():\n    assert True\n");
		Path marker = tmp.resolve("target-code-executed-" + payloadPath.replace('/', '_'));
		Path payload = skill.resolve(payloadPath);
		Files.createDirectories(payload.getParent());
		Files.writeString(payload, "boom\n");

		ValidationResult result = validator.validate(skill);

		assertTrue(result.isPassed());
		assertFalse(Files.exists(marker));
		Map<String, Object> metadata = discoveryDetail(result).getMetadata();
		assertEquals(false, metadata.get("execution_performed"));
		assertEquals(false, metadata.get("coverage_measured"));
	}

	static Stream<Arguments> payloadPaths() {
		return Stream.of(Arguments.of("pytest.py"), Arguments.of("pytest/__main__.py"));
	}

	@Test
	void staticDiscoveryCountsFilenameCandidatesWithoutParsingOrExecution() throws IOException {
		Path skill = skillDir("skill-discovery");
		Path testsDir = skill.resolve("tests");
		Files.createDirectories(testsDir);
		Files.writeString(testsDir.resolve("test_prefix.py"), "def test_prefix():\n    assert True\n");
		// Invalid UTF-8 proves discovery does not parse or import candidates.
		Files.write(testsDir.resolve("suffix_test.py"), new byte[] { (byte) 0xff, (byte) 0xfe, 0x00 });

		ValidationResult result = validator.checkTestPresence(skill);
		Map<String, Object> metadata = discoveryDetail(result).getMetadata();

		assertEquals(Map.of("test_count", 2, "execution_performed", false, "coverage_measured", false, "patterns",
				List.of("test_*.py", "*_test.py")), metadata);
		assertTrue(discoveryDetail(result).getMessage().contains("were not executed"));
		assertTrue(discoveryDetail(result).getMessage().contains("coverage was not measured"));
	}

	@Test
	void noStandardPythonTestsReportsUnmeasuredCoverageTruthfully() throws IOException {
		Path skill = skillDir("skill-notests");
		Files.createDirectories(skill.resolve("tests"));

		ValidationResult result = validator.checkTestPresence(skill);
		Map<String, Object> metadata = discoveryDetail(result).getMetadata();

		assertEquals(0, metadata.get("test_count"));
		assertEquals(false, metadata.get("execution_performed"));
		assertEquals(false, metadata.get("coverage_measured"));
		assertTrue(discoveryDetail(result).getMessage().contains("No standard Python test-file candidates found"));
		assertTrue(discoveryDetail(result).getMessage().contains("coverage was not measured"));
		assertTrue(result.getWarnings().stream().anyMatch(w -> w.toLowerCase().contains("consider adding tests")));
	}

	@Test
	void staticDiscoveryExcludesArtifactDirectories() throws IOException {
		Path skill = skillDir("skill-artifacts");
		Path testsDir = skill.resolve("tests");
		Files.createDirectories(testsDir);
		Files.writeString(testsDir.resolve("test_regular.py"), "def test_regular():\n    assert True\n");
		Files.createDirectories(skill.resolve("evals/results"));
		Files.writeString(skill.resolve("evals/results/test_snapshot.py"), "raise RuntimeError\n");
		Files.createDirectories(skill.resolve("test_directory.py")); // directory, not a
																		// file

		ValidationResult result = validator.checkTestPresence(skill);

		assertEquals(1, discoveryDetail(result).getMetadata().get("test_count"));
		assertEquals(1, result.getSummary().getFilesScanned());
	}

	@Test
	void staticDiscoveryExcludesSymlinksOutsideSkill() throws IOException {
		Path skill = skillDir("skill-symlink");
		Path testsDir = skill.resolve("tests");
		Files.createDirectories(testsDir);
		Files.writeString(testsDir.resolve("test_regular.py"), "def test_regular():\n    assert True\n");
		Path outsideDir = tmp.resolve("outside");
		Files.createDirectories(outsideDir);
		Path outsideTest = outsideDir.resolve("outside_test.py");
		Files.writeString(outsideTest, "raise RuntimeError\n");
		try {
			Files.createSymbolicLink(testsDir.resolve("test_file_link.py"), outsideTest);
		}
		catch (IOException | UnsupportedOperationException e) {
			return; // symlinks unavailable on this filesystem
		}

		ValidationResult result = validator.checkTestPresence(skill);

		assertEquals(1, discoveryDetail(result).getMetadata().get("test_count"));
		assertEquals(1, result.getSummary().getFilesScanned());
	}

	// ------------------------------------------------------------------
	// Dead links
	// ------------------------------------------------------------------

	@Test
	void detectsDeadLinks() throws IOException {
		Path skill = skillDir("dead-links-skill");
		Files.writeString(skill.resolve("SKILL.md"), """
				---
				name: dead-links-skill
				description: A skill with dead links for testing
				version: 1.0.0
				---

				# Dead Links Test

				See the [documentation](./docs/README.md) for more info.
				Check the [config file](./config/settings.yaml) for settings.
				""");

		ValidationResult result = validator.validate(skill);

		assertTrue(result.getErrors()
			.stream()
			.anyMatch(e -> e.toLowerCase().contains("dead link") || e.contains("docs/README.md")));
		assertTrue(result.getErrors()
			.stream()
			.anyMatch(e -> e.toLowerCase().contains("dead link") || e.contains("config/settings.yaml")));
	}

	@ParameterizedTest
	@MethodSource("commonmarkLinkCases")
	void commonmarkDeadLinksAreDetected(String content, String target) throws IOException {
		Path skill = skillDir("commonmark-" + target.replace('.', '_'));
		Files.writeString(skill.resolve("SKILL.md"), content);

		ValidationResult result = validator.checkDeadLinks(skill);

		assertEquals(List.of("Dead link in SKILL.md: " + target), result.getErrors());
	}

	static Stream<Arguments> commonmarkLinkCases() {
		return Stream.of(
				Arguments.of("See [the guide][guide].\n\n[guide]: missing-reference.md\n", "missing-reference.md"),
				Arguments.of("See [guide][].\n\n[guide]: missing-collapsed.md\n", "missing-collapsed.md"),
				Arguments.of("See [guide].\n\n[guide]: missing-shortcut.md\n", "missing-shortcut.md"),
				Arguments.of("See <a href=\"missing-html.md\">the guide</a>.\n", "missing-html.md"),
				Arguments.of("![diagram](missing-image.png)\n", "missing-image.png"));
	}

	@Test
	void commonmarkNonNavigationContextsAreIgnored() throws IOException {
		Path skill = skillDir("inert-links");
		Files.writeString(skill.resolve("SKILL.md"),
				"---\ndescription: \"[metadata](frontmatter.md)\"\n---\n\n"
						+ "`[inline](inline.md)`\n\n```markdown\n[fenced](fenced.md)\n```\n\n"
						+ "<!-- <a href='comment.md'>comment</a> -->\n"
						+ "<template><a href='template.md'>template</a></template>\n");

		ValidationResult result = validator.checkDeadLinks(skill);

		assertEquals(List.of(), result.getErrors());
	}

	@Test
	void commonmarkLocalTargetsAreNormalizedBeforeLookup() throws IOException {
		Path skill = skillDir("normalized-links");
		Files.writeString(skill.resolve("my file.md"), "# Existing\n");
		Files.writeString(skill.resolve("guide.md"), "# Existing\n");
		Files.createDirectories(skill.resolve("docs"));
		Files.writeString(skill.resolve("SKILL.md"), "[space](<my file.md>)\n[query](guide.md?view=1)\n"
				+ "[fragment](guide.md#overview)\n[directory](docs/)\n");

		ValidationResult result = validator.checkDeadLinks(skill);

		assertEquals(List.of(), result.getErrors());
	}

	@Test
	void missingDirectoryTargetIsReported() throws IOException {
		Path skill = skillDir("missing-directory");
		Files.writeString(skill.resolve("SKILL.md"), "[docs](missing-dir/)\n");

		ValidationResult result = validator.checkDeadLinks(skill);

		assertEquals(List.of("Dead link in SKILL.md: missing-dir/"), result.getErrors());
	}

	@Test
	void nonLocalAndRootAbsoluteTargetsAreIgnored() throws IOException {
		Path skill = skillDir("non-local-links");
		Files.writeString(skill.resolve("SKILL.md"),
				"[root](/config/settings.yaml)\n[protocol](//example.com/missing.md)\n"
						+ "[web](https://example.com/missing.md)\n[email](mailto:user@example.com)\n"
						+ "[anchor](#overview)\n");

		ValidationResult result = validator.checkDeadLinks(skill);

		assertEquals(List.of(), result.getErrors());
	}

	@Test
	void repeatedDeadTargetsAreReportedOnce() throws IOException {
		Path skill = skillDir("duplicate-links");
		Files.writeString(skill.resolve("SKILL.md"),
				"[first](missing.md), [second](./missing.md#part), and [third][same].\n\n"
						+ "[same]: missing.md?view=1\n");

		ValidationResult result = validator.checkDeadLinks(skill);

		assertEquals(List.of("Dead link in SKILL.md: missing.md"), result.getErrors());
	}

	@Test
	void validRelativeLinksPass() throws IOException {
		Path skill = skillDir("valid-links-skill");
		Files.createDirectories(skill.resolve("docs"));
		Files.writeString(skill.resolve("docs/README.md"), "# Documentation");
		Files.writeString(skill.resolve("SKILL.md"), """
				---
				name: valid-links-skill
				description: A skill with valid links
				version: 1.0.0
				---

				# Valid Links Test

				See the [documentation](./docs/README.md) for more info.
				""");

		ValidationResult result = validator.validate(skill);

		assertTrue(result.getErrors().stream().noneMatch(e -> e.toLowerCase().contains("dead link")));
	}

	@Test
	void externalLinksIgnored() throws IOException {
		Path skill = skillDir("external-links-skill");
		Files.writeString(skill.resolve("SKILL.md"), """
				---
				name: external-links-skill
				description: A skill with external links
				---

				Visit [NVIDIA](https://nvidia.com) for more info.
				Check [GitHub](https://github.com/nvidia) for code.
				Send [email](mailto:support@nvidia.com) for help.
				""");

		ValidationResult result = validator.validate(skill);

		assertEquals(List.of(), result.getErrors());
	}

	// ------------------------------------------------------------------
	// Dependency audit
	// ------------------------------------------------------------------

	@Test
	void detectsUnpinnedDependencies() throws IOException {
		Path skill = skillDir("unpinned-deps-skill");
		writeSkillManifest(skill);
		Files.writeString(skill.resolve("requirements.txt"),
				"\n# Pinned\nrequests==2.28.0\npydantic>=2.0.0\n\n# Unpinned (should warn)\nnumpy\npandas\n");

		ValidationResult result = validator.validate(skill);

		assertTrue(Stream.concat(result.getErrors().stream(), result.getWarnings().stream())
			.anyMatch(m -> m.toLowerCase().contains("unpinned")));
	}

	private Path uniqueRequirementsFile(String prefix, String requirement, String trailingNewline) throws IOException {
		Path dir = tmp.resolve(prefix + "-" + Math.abs(requirement.hashCode()));
		Files.createDirectories(dir);
		Path file = dir.resolve("requirements.txt");
		Files.writeString(file, requirement + trailingNewline);
		return file;
	}

	@ParameterizedTest
	@MethodSource("markerRequirements")
	void environmentMarkerComparisonsDoNotHideUnpinnedRequirements(String requirement) throws IOException {
		Path requirements = uniqueRequirementsFile("marker", requirement, "\n");

		ValidationResult result = validator.checkRequirementsFile(requirements);

		assertEquals(List.of(), result.getErrors());
		assertEquals(List.of("requirements.txt:1 - Unpinned: " + requirement), result.getWarnings());
	}

	static Stream<Arguments> markerRequirements() {
		return Stream.of(Arguments.of("requests; python_version < \"3.13\""),
				Arguments.of("requests; python_version != \"3.12\""), Arguments.of("requests;python_version<\"3.13\""),
				Arguments.of("requests ; python_version < \"3.13\""),
				Arguments.of("requests; sys_platform == \"win32\""),
				Arguments.of("requests; implementation_name == \"cpython@corp\""),
				Arguments.of("requests[security]; python_version >= \"3.9\""));
	}

	@ParameterizedTest
	@MethodSource("commentRequirements")
	void inlineCommentsDoNotHideUnpinnedRequirements(String requirement) throws IOException {
		Path requirements = uniqueRequirementsFile("comment", requirement, "\n");

		ValidationResult result = validator.checkRequirementsFile(requirements);

		assertEquals(List.of(), result.getErrors());
		assertEquals(List.of("requirements.txt:1 - Unpinned: " + requirement), result.getWarnings());
	}

	static Stream<Arguments> commentRequirements() {
		return Stream.of(Arguments.of("requests # owner@example.com"),
				Arguments.of("requests[security] # contact @ owner"), Arguments.of("requests # consider >=2 later"));
	}

	@ParameterizedTest
	@MethodSource("emptyDirectReferences")
	void emptyDirectReferencesAreNotTreatedAsPinned(String requirement) throws IOException {
		Path requirements = uniqueRequirementsFile("direct", requirement, "\n");

		ValidationResult result = validator.checkRequirementsFile(requirements);

		assertEquals(List.of(), result.getErrors());
		assertEquals(List.of("requirements.txt:1 - Unpinned: " + requirement), result.getWarnings());
	}

	static Stream<Arguments> emptyDirectReferences() {
		return Stream.of(Arguments.of("requests @"), Arguments.of("requests @ # owner@example.com"),
				Arguments.of("requests @ ; python_version < \"3.13\""));
	}

	@ParameterizedTest
	@MethodSource("constrainedRequirements")
	void markerHandlingPreservesConstrainedAndDirectRequirements(String requirement) throws IOException {
		Path requirements = uniqueRequirementsFile("constrained", requirement, "\n");

		ValidationResult result = validator.checkRequirementsFile(requirements);

		assertEquals(List.of(), result.getErrors());
		assertEquals(List.of(), result.getWarnings());
	}

	static Stream<Arguments> constrainedRequirements() {
		return Stream.of(Arguments.of("requests>=2; python_version < \"3.13\""), Arguments.of("requests>=2.0,<3.0"),
				Arguments.of("requests==2.31.0"), Arguments.of("requests~=2.31"), Arguments.of("requests!=2.30.0"),
				Arguments.of("requests>=2 # owner@example.com"),
				Arguments.of("requests @ https://example.invalid/requests.whl"),
				Arguments.of("requests @ https://example.invalid/a;v=1/requests.whl"),
				Arguments.of("requests @ https://example.invalid/requests.whl#sha256=abc123"),
				Arguments.of("requests @ https://example.invalid/requests.whl # owner@example.com"),
				Arguments.of("requests @ https://example.invalid/requests.whl ; python_version < \"3.13\""));
	}

	@Test
	void bannedRequirementWithMarkerRemainsAnError() throws IOException {
		Path requirements = uniqueRequirementsFile("banned-marker", "pycrypto; python_version < \"3.13\"", "\n");

		ValidationResult result = validator.checkRequirementsFile(requirements);

		assertEquals(List.of("requirements.txt:1 - Banned package: pycrypto"), result.getErrors());
		assertEquals(List.of(), result.getWarnings());
	}

	@Test
	void detectsBannedPackages() throws IOException {
		Path skill = skillDir("banned-deps-skill");
		writeSkillManifest(skill);
		Files.writeString(skill.resolve("requirements.txt"), "\npycrypto==2.6.1\nsubprocess32==3.5.4\n");

		ValidationResult result = validator.validate(skill);

		assertTrue(result.getErrors()
			.stream()
			.anyMatch(e -> e.toLowerCase().contains("banned") || e.toLowerCase().contains("pycrypto")
					|| e.toLowerCase().contains("subprocess32")));
	}

	@Test
	void noRequirementsIsOk() throws IOException {
		Path skill = skillDir("no-req-skill");
		writeSkillManifest(skill);

		ValidationResult result = validator.validate(skill);

		assertTrue(result.getErrors().stream().noneMatch(e -> e.toLowerCase().contains("requirements")));
	}

}
