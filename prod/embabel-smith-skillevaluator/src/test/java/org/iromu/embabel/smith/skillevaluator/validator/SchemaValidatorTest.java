/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
 * Ports of the key cases from {@code tests/validators/test_schema.py}: the frontmatter
 * schema, naming and structure checks, the policy-driven author and {@code metadata.id}
 * rules, and the harness-portability advisories.
 */
class SchemaValidatorTest {

	private final SchemaValidator validator = new SchemaValidator();

	@TempDir
	Path tmp;

	private static final String CLEAN_BODY = "\n# Clean Skill\n\n## Instructions\n\n1. Run.\n\n## Examples\n\nExample.\n";

	// ------------------------------------------------------------------
	// Fixtures
	// ------------------------------------------------------------------

	/**
	 * Writes {@code root/name/SKILL.md} with the given frontmatter body (no leading
	 * {@code ---}).
	 */
	private Path writeSkill(Path root, String name, String frontmatter, String body) throws IOException {
		Path dir = root.resolve(name);
		Files.createDirectories(dir);
		Files.writeString(dir.resolve("SKILL.md"), "---\n" + frontmatter + "---\n" + body);
		return dir;
	}

	private Path writeCleanSkill(Path root, String name) throws IOException {
		return writeSkill(root, name,
				"name: " + name + "\n"
						+ "description: Use when testing the schema validator. A fixture skill for the test suite.\n"
						+ "metadata:\n  author: Test User <testuser@example.com>\n",
				CLEAN_BODY);
	}

	private static List<Finding> checks(ValidationResult result, String checkName) {
		return result.getFindings().stream().filter(finding -> finding.getCheckName().equals(checkName)).toList();
	}

	private static SuccessDetail detail(ValidationResult result, String checkName) {
		return result.getSuccessDetails()
			.stream()
			.filter(entry -> entry.getCheckName().equals(checkName))
			.findFirst()
			.orElseThrow();
	}

	// ------------------------------------------------------------------
	// Valid skills
	// ------------------------------------------------------------------

	@Test
	void validSkillPasses() throws IOException {
		Path skill = writeSkill(tmp.resolve("skills"), "sample-skill",
				"name: sample-skill\n" + "description: A sample skill for testing SkillEvaluator validation."
						+ " Use when demonstrating validation workflows.\n" + "license: MIT\n"
						+ "compatibility: Requires Python 3.11+\n" + "metadata:\n"
						+ "  author: Test User <testuser@example.com>\n" + "  tags:\n    - testing\n    - sample\n"
						+ "  languages:\n    - python\n" + "  frameworks:\n    - pytest\n"
						+ "  domain:\n    - testing\n    - quality\n",
				CLEAN_BODY);

		ValidationResult result = validator.validate(skill);

		assertTrue(result.isPassed(), () -> "Expected valid skill to pass. Errors: " + result.getErrors());
		assertTrue(result.getErrors().isEmpty());
		assertEquals("skills/sample-skill/",
				detail(result, "folder_hierarchy").getMessage().replace("Valid general skill structure: ", ""));
		assertEquals("Directory name matches frontmatter: 'sample-skill'",
				detail(result, "name_consistency").getMessage());
		assertEquals("Valid author format: Test User <testuser@example.com>",
				detail(result, "author_format").getMessage());
		SchemaFrontmatter frontmatter = (SchemaFrontmatter) result.getMetadata().get("frontmatter");
		assertNotNull(frontmatter);
		assertEquals(List.of("testing", "quality"), frontmatter.getDomain());
	}

	@Test
	void validTeamSkillPasses() throws IOException {
		Path skill = writeSkill(tmp.resolve("team-skills").resolve("example-team"), "langraph-debugging",
				"name: langraph-debugging\n" + "description: Debugging workflows for LangGraph applications."
						+ " Use when troubleshooting LangGraph mini-graphs.\n"
						+ "metadata:\n  author: Example Team <team@example.com>\n",
				CLEAN_BODY);

		ValidationResult result = validator.validate(skill);

		assertTrue(result.isPassed(), () -> "Expected team skill to pass. Errors: " + result.getErrors());
		assertEquals("Valid team skill structure: team-skills/example-team/.../langraph-debugging/",
				detail(result, "folder_hierarchy").getMessage());
	}

	@Test
	void unexpectedItemsInSkillRootAreAdvisory() throws IOException {
		Path skill = writeCleanSkill(tmp.resolve("skills"), "clean-skill");
		Files.createDirectories(skill.resolve("references"));
		Files.writeString(skill.resolve("notes.txt"), "hi\n");

		ValidationResult result = validator.validate(skill);

		assertTrue(result.isPassed(), () -> "Advisories must not block. Errors: " + result.getErrors());
		List<String> unexpected = checks(result, "unexpected_file").stream().map(Finding::getMessage).toList();
		if (System.getenv("SKILLEVALUATOR_SCHEMA_ALLOWED_DIRS") == null) {
			assertEquals(List.of("Unexpected 'notes.txt' in skill root"), unexpected,
					"references/ must be allowed and notes.txt flagged");
		}
	}

	// ------------------------------------------------------------------
	// Manifest presence and casing
	// ------------------------------------------------------------------

	@Test
	void directoryWithoutManifestIsNotASkill() throws IOException {
		Path empty = Files.createDirectories(tmp.resolve("empty-skill"));

		ValidationResult result = validator.validate(empty);

		// A directory without SKILL.md is treated as a collection root, so the
		// manifest_exists finding (only raised for a single skill) is unreachable
		// here; upstream reports discovery failure instead.
		List<Finding> findings = checks(result, "skill_discovery");
		assertFalse(result.isPassed());
		assertEquals(1, findings.size());
		assertEquals(Severity.HIGH, findings.get(0).getSeverity());
		assertEquals("No skills found in target directory", findings.get(0).getMessage());
	}

	@Test
	void emptyFolderReportsNoSkills() throws IOException {
		Files.createDirectories(tmp.resolve("nothing-here"));

		ValidationResult result = validator.validate(tmp);

		assertFalse(result.isPassed());
		assertTrue(result.getErrors().stream().anyMatch(err -> err.contains("No skills found")));
	}

	@Test
	void lowercaseManifestIsFlagged() throws IOException {
		Path dir = tmp.resolve("lowercase-skill");
		Files.createDirectories(dir);
		Files.writeString(dir.resolve("skill.md"),
				"---\nname: lowercase-skill\ndescription: A skill with lowercase skill.md for testing\n---"
						+ CLEAN_BODY);

		List<Finding> findings = checks(validator.validate(dir), "manifest_naming");

		assertEquals(1, findings.size());
		assertTrue(findings.get(0).getMessage().contains("SKILL.md"));
		assertTrue(findings.get(0).getMessage().contains("agentskills.io"));
	}

	@Test
	void uppercaseManifestIsNotFlagged() throws IOException {
		Path skill = writeCleanSkill(tmp, "uppercase-skill");

		assertTrue(checks(validator.validate(skill), "manifest_naming").isEmpty());
	}

	// ------------------------------------------------------------------
	// Frontmatter schema
	// ------------------------------------------------------------------

	@Test
	void missingRequiredFieldFails() throws IOException {
		Path skill = writeSkill(tmp, "incomplete-skill", "name: incomplete-skill\n", "\n# Incomplete\n");

		ValidationResult result = validator.validate(skill);

		assertFalse(result.isPassed());
		List<Finding> findings = checks(result, "frontmatter_field");
		assertEquals(1, findings.size());
		assertEquals("Field 'description': Field required", findings.get(0).getMessage());
		assertEquals("missing", findings.get(0).getMetadata().get("error_type"));
	}

	@Test
	void forbiddenFieldsRejected() throws IOException {
		Path skill = writeSkill(tmp, "forbidden-fields",
				"name: forbidden-fields\ndescription: A skill that uses forbidden fields per spec\n"
						+ "alwaysApply: true\nglobs:\n  - \"*.py\"\n",
				"\n# Forbidden Fields Skill\n");

		ValidationResult result = validator.validate(skill);

		assertFalse(result.isPassed());
		Finding finding = checks(result, "frontmatter_field").get(0);
		assertTrue(finding.getMessage().startsWith("Field '': Value error, Forbidden fields detected:"),
				finding.getMessage());
		assertTrue(finding.getMessage().contains("alwaysApply, globs"), finding.getMessage());
	}

	@Test
	void nameMustMatchDirectory() throws IOException {
		Path skill = writeSkill(tmp, "directory-name",
				"name: different-name\ndescription: Directory and frontmatter differ\n", "\n# Name Mismatch Skill\n");

		ValidationResult result = validator.validate(skill);

		assertFalse(result.isPassed());
		Finding finding = checks(result, "name_consistency").get(0);
		assertEquals("Directory name 'directory-name' doesn't match frontmatter name 'different-name'",
				finding.getMessage());
		assertEquals(Severity.HIGH, finding.getSeverity());
	}

	@Test
	void nameLengthAndHyphenRules() throws IOException {
		String longName = "a".repeat(65) + "-skill";
		Path longDir = writeSkill(tmp, longName, "name: " + longName + "\ndescription: A skill with a too long name\n",
				"\n# Long Name Skill\n");
		assertTrue(checks(validator.validate(longDir), "frontmatter_field").stream()
			.anyMatch(f -> f.getMessage().contains("String should have at most 64 characters")));

		Path badName = writeSkill(tmp, "bad--name",
				"name: bad--name\ndescription: A skill with consecutive hyphens in name\n", "\n# Bad Name Skill\n");
		// The kebab-case pattern itself already rejects consecutive hyphens, so
		// upstream's dedicated "cannot contain consecutive hyphens" branch is dead
		// code and the kebab-case message is the one that surfaces.
		assertTrue(checks(validator.validate(badName), "frontmatter_field").stream()
			.anyMatch(f -> f.getMessage().contains("Skill name 'bad--name' must be in kebab-case format")));

		Path underscore = writeSkill(tmp, "invalid-skill",
				"name: Invalid_Skill_Name\ndescription: A skill with an invalid name format\n", "\n# Invalid Skill\n");
		ValidationResult result = validator.validate(underscore);
		assertFalse(result.isPassed());
		assertTrue(checks(result, "frontmatter_field").stream()
			.anyMatch(f -> f.getMessage().contains("must be in kebab-case format")));
	}

	@Test
	void descriptionLengthConstraint() throws IOException {
		Path skill = writeSkill(tmp, "long-description",
				"name: long-description\ndescription: " + "A".repeat(1030) + "\n", "\n# Long Description Skill\n");

		ValidationResult result = validator.validate(skill);

		assertFalse(result.isPassed());
		assertTrue(checks(result, "frontmatter_field").stream()
			.anyMatch(f -> f.getMessage().contains("String should have at most 1024 characters")));
	}

	@Test
	void reservedWordsRejectedButSubstringsAllowed() throws IOException {
		for (String word : List.of("anthropic", "claude")) {
			Path skill = writeSkill(tmp, "my-" + word + "-skill",
					"name: my-" + word + "-skill\ndescription: A skill with a reserved word in its name\n", CLEAN_BODY);
			ValidationResult result = validator.validate(skill);
			assertFalse(result.isPassed(), "Name containing '" + word + "' should be rejected");
			assertTrue(checks(result, "frontmatter_field").stream()
				.anyMatch(f -> f.getMessage().contains("must not contain reserved words")), word);
		}

		Path allowed = writeSkill(tmp, "philanthropic-grant-matcher",
				"name: philanthropic-grant-matcher\n"
						+ "description: Use when matching philanthropic grants to eligible programs.\n"
						+ "metadata:\n  author: Test User <test@nvidia.com>\n",
				CLEAN_BODY);
		ValidationResult result = validator.validate(allowed);
		assertTrue(result.isPassed(), () -> result.getErrors().toString());
	}

	@ParameterizedTest
	@MethodSource("xmlTagCases")
	void xmlTagsInFrontmatterRejected(String name, String description) throws IOException {
		Path skill = writeSkill(tmp, name, "name: " + name + "\ndescription: \"" + description + "\"\n"
				+ "metadata:\n  author: Test User <test@nvidia.com>\n", CLEAN_BODY);

		assertFalse(validator.validate(skill).isPassed(), name);
	}

	static Stream<Arguments> xmlTagCases() {
		return Stream.of(Arguments.of("xml-desc", "A skill <script>alert('xss')</script> with injected tags"),
				Arguments.of("unclosed-xml-desc", "A skill with an unclosed <script foo tag in the description"),
				Arguments.of("closing-xml-desc", "Use when validating a closing </tool> tag."),
				Arguments.of("self-closing-xml-desc", "Use when validating an injected <tool/> tag."));
	}

	@Test
	void comparisonOperatorsInDescriptionAllowed() throws IOException {
		Path skill = writeSkill(tmp, "threshold-monitor",
				"name: threshold-monitor\n"
						+ "description: \"Use this skill when free disk space < 10GB and CPU usage > 90% to cleanup\"\n"
						+ "metadata:\n  author: Test User <test@example.com>\n",
				CLEAN_BODY);

		ValidationResult result = validator.validate(skill);
		assertTrue(result.isPassed(), () -> "Expected comparator description to pass: " + result.getErrors());
	}

	@ParameterizedTest
	@MethodSource("nonStringKeys")
	void nonStringKeyReturnsFieldError(String key) throws IOException {
		Path skill = writeSkill(tmp, "non-string-key",
				"name: non-string-key\ndescription: A skill whose frontmatter has a non-string key\n" + "metadata:\n  "
						+ key + ": extra value\n",
				CLEAN_BODY);

		ValidationResult result = validator.validate(skill);

		assertFalse(result.isPassed());
		String field = "metadata." + key;
		assertTrue(
				checks(result, "frontmatter_field").stream()
					.anyMatch(f -> f.getMessage().equals("Field '" + field + "': Keys should be strings")),
				() -> result.getFindings().toString());
		assertTrue(checks(result, "frontmatter_field").stream()
			.anyMatch(f -> field.equals(f.getMetadata().get("field"))
					&& "invalid_key".equals(f.getMetadata().get("error_type"))));
	}

	static Stream<Arguments> nonStringKeys() {
		return Stream.of("123", "true", "false", "null", "2026-01-01").map(Arguments::of);
	}

	@Test
	void invalidMetadataDomainReportsOneFieldFinding() throws IOException {
		Path skill = writeSkill(tmp, "bad-domain", "name: bad-domain\ndescription: A skill with an invalid domain\n"
				+ "metadata:\n  author: Test User <testuser@example.com>\n" + "  domain:\n    - backend\n    - 42\n",
				"\n# Bad Domain\n");

		ValidationResult result = validator.validate(skill);

		assertFalse(result.isPassed());
		List<Finding> findings = checks(result, "frontmatter_field");
		assertEquals(1, findings.size(), () -> findings.stream().map(Finding::getMessage).toList().toString());
		assertEquals(Severity.HIGH, findings.get(0).getSeverity());
		assertEquals("metadata.domain", findings.get(0).getMetadata().get("field"));
		assertTrue(findings.get(0).getMessage().contains("domain values must be strings"),
				findings.get(0).getMessage());
	}

	@Test
	void yamlSyntaxErrorIsHigh() throws IOException {
		Path skill = writeSkill(tmp, "bad-yaml", "name: bad-yaml\ndescription: A skill with broken yaml\n  oops: yes\n",
				"\n# Bad Yaml\n");

		ValidationResult result = validator.validate(skill);

		assertFalse(result.isPassed());
		assertEquals(1, checks(result, "yaml_syntax").size());
	}

	@Test
	void missingFrontmatterBlockIsHigh() throws IOException {
		Path dir = tmp.resolve("no-frontmatter");
		Files.createDirectories(dir);
		Files.writeString(dir.resolve("SKILL.md"), "# Just a heading\n");

		ValidationResult result = validator.validate(dir);

		assertFalse(result.isPassed());
		Finding finding = checks(result, "frontmatter_format").get(0);
		assertEquals(1, finding.getLineNumber().intValue());
	}

	// ------------------------------------------------------------------
	// Body content
	// ------------------------------------------------------------------

	@Test
	void missingTopLevelHeadingRejected() throws IOException {
		Path skill = writeSkill(tmp, "no-heading",
				"name: no-heading\ndescription: A skill missing a top-level heading in the body\n",
				"\nThis body has no heading at all.\n\n## Instructions\n\n1. Do things\n\n## Examples\n\nExample.\n");

		ValidationResult result = validator.validate(skill);

		assertFalse(result.isPassed());
		assertEquals(Severity.HIGH, checks(result, "body_heading").get(0).getSeverity());
	}

	@Test
	void missingRecommendedSectionsAreMedium() throws IOException {
		Path skill = writeSkill(tmp, "no-examples",
				"name: no-examples\ndescription: A skill missing the Examples section\n"
						+ "metadata:\n  author: Body User <bodyuser@example.com>\n",
				"\n# No Examples\n\n## Instructions\n\n1. Do something\n");

		ValidationResult result = validator.validate(skill);

		List<Finding> findings = checks(result, "body_recommended_section");
		assertEquals(1, findings.size());
		assertEquals(Severity.MEDIUM, findings.get(0).getSeverity());
		assertTrue(findings.get(0).getMessage().contains("Examples"));
		assertTrue(checks(result, "body_required_section").isEmpty());
	}

	@Test
	void usageSectionSatisfiesInstructionsNudge() throws IOException {
		Path skill = writeSkill(tmp, "usage-skill",
				"name: usage-skill\ndescription: A skill that uses Usage instead of Instructions\n",
				"\n# Usage Skill\n\n## Usage\n\n1. Run the tool\n\n## Examples\n\n```bash\nrun-tool --help\n```\n");

		List<Finding> findings = checks(validator.validate(skill), "body_recommended_section");

		assertTrue(findings.stream().noneMatch(f -> f.getMessage().contains("Instructions")),
				() -> "## Usage should satisfy the Instructions nudge: " + findings);
	}

	// ------------------------------------------------------------------
	// Author policy
	// ------------------------------------------------------------------

	private Path authorSkill(String name, String authorLine) throws IOException {
		String metadataBlock = authorLine == null ? "" : "metadata:\n  author: " + authorLine + "\n";
		return writeSkill(tmp, name, "name: " + name + "\n"
				+ "description: A profile-aware skill used by the test suite for severity overrides\n" + metadataBlock,
				CLEAN_BODY);
	}

	@Test
	void authorNameOnlyIsAdvisory() throws IOException {
		ValidationResult result = validator.validate(authorSkill("no-email-author", "John Doe"));

		assertTrue(result.isPassed(), () -> result.getErrors().toString());
		assertTrue(checks(result, "author_format").isEmpty());
		List<Finding> findings = checks(result, "author_without_email");
		assertEquals(1, findings.size());
		assertEquals(Severity.INFO, findings.get(0).getSeverity());
		assertEquals("Author has no email address: John Doe", findings.get(0).getMessage());
		assertEquals(Boolean.FALSE, findings.get(0).getMetadata().get("shape_ok"));
	}

	@Test
	void authorMissingIsLow() throws IOException {
		ValidationResult result = validator.validate(authorSkill("missing-author", null));

		assertTrue(result.isPassed(), () -> result.getErrors().toString());
		List<Finding> findings = checks(result, "author_missing");
		assertEquals(1, findings.size());
		assertEquals(Severity.LOW, findings.get(0).getSeverity());
		assertTrue(findings.get(0).getMessage().contains("Author not specified"));
	}

	@ParameterizedTest
	@MethodSource("malformedAuthors")
	void malformedAuthorBlocks(String author, String reason) throws IOException {
		ValidationResult result = validator.validate(authorSkill("bad-author", "\"" + author + "\""));

		List<Finding> findings = checks(result, "author_format");
		assertEquals(1, findings.size(), () -> result.getFindings().toString());
		assertEquals(Severity.HIGH, findings.get(0).getSeverity());
		assertTrue(findings.get(0).getMessage().contains(reason), findings.get(0).getMessage());
		assertTrue(findings.get(0).getLineContent().length() <= "author: ".length() + 256);
		assertFalse(result.isPassed());
	}

	static Stream<Arguments> malformedAuthors() {
		return Stream.of(Arguments.of("jane@example.com", "contains an email address"),
				Arguments.of("Jane Doe jane@example.com", "contains an email address"),
				Arguments.of("root@buildhost", "contains an email address"),
				Arguments.of("Jos\u00e9@example.com", "contains an email address"),
				Arguments.of("Jane Doe <jane>", "angle brackets"),
				Arguments.of("Jane\u202eDoe", "control, private-use, or bidirectional formatting"),
				Arguments.of("Jane\tDoe", "control, private-use, or bidirectional formatting"),
				Arguments.of("Jane\ue000Doe", "control, private-use, or bidirectional formatting"),
				Arguments.of("---", "no letters or digits"),
				Arguments.of("J".repeat(257), "longer than 256 characters"));
	}

	@ParameterizedTest
	@MethodSource("acceptableAuthors")
	void wellFormedOrPlainAttributionDoesNotBlock(String author) throws IOException {
		ValidationResult result = validator.validate(authorSkill("author-ok", "\"" + author + "\""));

		assertTrue(result.isPassed(), () -> result.getErrors().toString());
		if (author.contains("<")) {
			assertTrue(checks(result, "author_format").isEmpty() && checks(result, "author_without_email").isEmpty(),
					() -> result.getFindings().toString());
		}
	}

	static Stream<Arguments> acceptableAuthors() {
		return Stream.of(Arguments.of("Jane Doe <jane@example.com>"), Arguments.of("NVIDIA cuOpt Team"),
				Arguments.of("Jane Doe (@jdoe)"), Arguments.of("\u00c9quipe Donn\u00e9es"),
				Arguments.of("Jane\u200cDoe <jane@example.com>"), Arguments.of("Jane\u200dDoe <jane@example.com>"),
				Arguments.of("Jane\u00adDoe <jane@example.com>"));
	}

	// ------------------------------------------------------------------
	// metadata.id contract
	// ------------------------------------------------------------------

	private Path idSkill(Path root, String name, String idLine) throws IOException {
		String idBlock = idLine == null ? "" : "  id: " + idLine + "\n";
		return writeSkill(root, name,
				"name: " + name + "\n"
						+ "description: Use when testing the metadata.id contract. A fixture skill for the suite\n"
						+ "metadata:\n  author: Jane Doe <jane@example.com>\n" + idBlock,
				CLEAN_BODY);
	}

	@Test
	void validIdPassesAndRecordsIt() throws IOException {
		Path skill = idSkill(tmp, "id-ok", "id-ok-2");

		ValidationResult result = validator.validate(skill);

		assertTrue(result.isPassed(), () -> result.getErrors().toString());
		assertTrue(checks(result, "metadata_id_format").isEmpty());
		assertTrue(detail(result, "metadata_id_format").getMessage()
			.contains("Uniqueness is checked only across skills validated together"));
		assertEquals("id-ok-2", result.getMetadata().get("metadata_id"));
	}

	@Test
	void missingIdIsIgnoredUnderTheDefaultProfile() throws IOException {
		ValidationResult result = validator.validate(idSkill(tmp, "no-id", null));

		assertTrue(result.isPassed(), () -> result.getErrors().toString());
		assertTrue(checks(result, "metadata_id_missing").isEmpty());
		assertTrue(checks(result, "metadata_id_format").isEmpty());
	}

	@ParameterizedTest
	@MethodSource("invalidIds")
	void invalidIdIsAdvisoryByDefault(String idLine, String reason) throws IOException {
		ValidationResult result = validator.validate(idSkill(tmp, "bad-id", idLine));

		assertTrue(result.isPassed(), () -> result.getErrors().toString());
		List<Finding> findings = checks(result, "metadata_id_format");
		assertEquals(1, findings.size());
		assertEquals(Severity.MEDIUM, findings.get(0).getSeverity());
		assertTrue(findings.get(0).getMessage().contains(reason), findings.get(0).getMessage());
		assertTrue(findings.get(0).getLineContent().length() <= "id: ".length() + 81);
		assertNull(result.getMetadata().get("metadata_id"));
	}

	static Stream<Arguments> invalidIds() {
		return Stream.of(Arguments.of("\"My-Skill\"", "lowercase ASCII"), Arguments.of("my_skill", "lowercase ASCII"),
				Arguments.of("-my-skill", "lowercase ASCII"), Arguments.of("my--skill", "lowercase ASCII"),
				Arguments.of("\" my-skill\"", "no surrounding whitespace"),
				Arguments.of("\"skill-\u00e9\"", "lowercase ASCII"),
				Arguments.of("\"" + "a".repeat(65) + "\"", "at most 64 characters (got 65)"),
				Arguments.of("\"\"", "must not be empty"), Arguments.of("123", "must be a string (got int)"),
				Arguments.of("true", "must be a string (got bool)"),
				Arguments.of("null", "must be a string (got NoneType)"),
				Arguments.of("[a, b]", "must be a string (got list)"));
	}

	@Test
	void collectionReportsDuplicateIds() throws IOException {
		Path skills = tmp.resolve("skills");
		idSkill(skills, "first-skill", "shared-id");
		idSkill(skills, "second-skill", "shared-id");
		idSkill(skills, "third-skill", "third-skill");
		idSkill(skills, "fourth-skill", null);

		ValidationResult result = validator.validate(tmp);

		List<Finding> duplicates = checks(result, "metadata_id_duplicate");
		assertEquals(1, duplicates.size());
		assertEquals(Severity.MEDIUM, duplicates.get(0).getSeverity());
		assertEquals(List.of("skills/first-skill", "skills/second-skill"),
				duplicates.get(0).getMetadata().get("skills"));
		assertTrue(duplicates.get(0).getMessage().contains("metadata.id 'shared-id' is declared by 2 skills"),
				duplicates.get(0).getMessage());
	}

	@Test
	void collectionWithUniqueIdsReportsScope() throws IOException {
		Path skills = tmp.resolve("skills");
		idSkill(skills, "first-skill", "first-skill");
		idSkill(skills, "second-skill", "second-skill");

		ValidationResult result = validator.validate(tmp);

		assertTrue(result.isPassed(), () -> result.getErrors().toString());
		assertTrue(checks(result, "metadata_id_duplicate").isEmpty());
		assertEquals("2 declared metadata.id value(s) are unique within this collection",
				detail(result, "metadata_id_unique").getMessage());
	}

	@Test
	void folderScanKeepsPassingSkillsAdvisories() throws IOException {
		Path skills = tmp.resolve("skills");
		Path team = idSkill(skills, "team-skill", "INVALID_ID");
		Files.writeString(team.resolve("SKILL.md"),
				Files.readString(team.resolve("SKILL.md")).replace("Jane Doe <jane@example.com>", "Example Team"));

		ValidationResult result = validator.validate(tmp);

		assertTrue(result.isPassed(), () -> result.getErrors().toString());
		assertEquals(List.of("author_without_email", "metadata_id_format"),
				result.getFindings().stream().map(Finding::getCheckName).sorted().toList());
		for (Finding finding : result.getFindings()) {
			assertEquals("team-skill", finding.getMetadata().get("collection_skill"));
			assertTrue(finding.getFilePath().startsWith("[team-skill] "), finding.getFilePath());
		}
		assertEquals(2, detail(result, "team-skill").getMetadata().get("advisory_findings"));
	}

	// ------------------------------------------------------------------
	// Portability
	// ------------------------------------------------------------------

	@Test
	void duplicateTopLevelKeyIsHigh() throws IOException {
		Path skill = writeSkill(tmp.resolve("skills"), "portable-skill",
				"name: portable-skill\ndescription: Use when checking harness portability of a skill.\n"
						+ "metadata:\n  author: Test User <testuser@example.com>\n" + "description: Second value.\n",
				CLEAN_BODY);

		ValidationResult result = validator.validate(skill);

		List<Finding> findings = checks(result, "duplicate_key");
		assertFalse(result.isPassed());
		assertEquals(List.of("description"), findings.stream().map(f -> f.getMetadata().get("key")).toList());
		assertEquals(Severity.HIGH, findings.get(0).getSeverity());
		assertEquals(6, findings.get(0).getLineNumber());
	}

	@Test
	void duplicateNestedKeyIsReportedWithPath() throws IOException {
		Path skill = writeSkill(tmp.resolve("skills"), "portable-skill",
				"name: portable-skill\ndescription: Use when checking harness portability of a skill.\n"
						+ "metadata:\n  author: Test User <testuser@example.com>\n"
						+ "  author: Other User <other@example.com>\n",
				CLEAN_BODY);

		assertEquals(List.of("metadata.author"),
				checks(validator.validate(skill), "duplicate_key").stream()
					.map(f -> f.getMetadata().get("key"))
					.toList());
	}

	@Test
	void sameKeyInDifferentMappingsIsNotDuplicate() throws IOException {
		Path skill = writeSkill(tmp.resolve("skills"), "portable-skill",
				"name: portable-skill\ndescription: Use when checking harness portability of a skill.\n"
						+ "metadata:\n  author: Test User <testuser@example.com>\n"
						+ "  tags:\n    - name: a\n    - name: b\n",
				CLEAN_BODY);

		assertTrue(checks(validator.validate(skill), "duplicate_key").isEmpty());
	}

	@Test
	void bomIsMediumAndDoesNotFail() throws IOException {
		Path skill = writeCleanSkill(tmp.resolve("skills"), "portable-skill");
		Path manifest = skill.resolve("SKILL.md");
		byte[] raw = Files.readAllBytes(manifest);
		byte[] withBom = new byte[raw.length + 3];
		System.arraycopy(new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF }, 0, withBom, 0, 3);
		System.arraycopy(raw, 0, withBom, 3, raw.length);
		Files.write(manifest, withBom);

		ValidationResult result = validator.validate(skill);

		assertTrue(result.isPassed(), () -> result.getErrors().toString());
		assertEquals(List.of(Severity.MEDIUM),
				checks(result, "manifest_bom").stream().map(Finding::getSeverity).toList());
		assertTrue(checks(result, "frontmatter_format").isEmpty());
	}

	@Test
	void symlinkedManifestIsMedium() throws IOException {
		Path skill = tmp.resolve("skills").resolve("portable-skill");
		Files.createDirectories(skill);
		Path target = tmp.resolve("real.md");
		Files.writeString(target,
				"---\nname: portable-skill\ndescription: Use when checking harness portability of a skill.\n"
						+ "metadata:\n  author: Test User <testuser@example.com>\n---" + CLEAN_BODY);
		Files.createSymbolicLink(skill.resolve("SKILL.md"), target);

		ValidationResult result = validator.validate(skill);

		assertTrue(result.isPassed(), () -> result.getErrors().toString());
		assertEquals(1, checks(result, "manifest_symlink").size());
	}

	@ParameterizedTest
	@MethodSource("sizeThresholds")
	void manifestSizeThresholds(int size, Severity severity) throws IOException {
		Path skill = writeCleanSkill(tmp.resolve("skills"), "portable-skill");
		Path manifest = skill.resolve("SKILL.md");
		byte[] current = Files.readAllBytes(manifest);
		byte[] padded = new byte[size];
		System.arraycopy(current, 0, padded, 0, current.length);
		for (int i = current.length; i < size; i++) {
			padded[i] = 'x';
		}
		Files.write(manifest, padded);

		List<Finding> findings = checks(validator.validate(skill), "manifest_size");

		assertEquals(List.of(severity), findings.stream().map(Finding::getSeverity).toList());
		assertEquals(size, findings.get(0).getMetadata().get("size_bytes"));
	}

	static Stream<Arguments> sizeThresholds() {
		return Stream.of(Arguments.of(32 * 1024 + 1, Severity.LOW), Arguments.of(50 * 1024 + 1, Severity.MEDIUM),
				Arguments.of(256_001, Severity.HIGH));
	}

	@Test
	void nestedManifestIsMedium() throws IOException {
		Path skill = writeCleanSkill(tmp.resolve("skills"), "portable-skill");
		Path inner = Files.createDirectories(skill.resolve("references").resolve("inner"));
		Files.writeString(inner.resolve("SKILL.md"), "---\nname: inner\n---\n");

		List<Finding> findings = checks(validator.validate(skill), "nested_manifest");

		assertEquals(1, findings.size());
		assertEquals(Severity.MEDIUM, findings.get(0).getSeverity());
		assertTrue(findings.get(0).getMessage().contains("references/inner/SKILL.md"), findings.get(0).getMessage());
	}

	@Test
	void nestedManifestUnderDependencyDirIsIgnored() throws IOException {
		Path skill = writeCleanSkill(tmp.resolve("skills"), "portable-skill");
		Path pkg = Files.createDirectories(skill.resolve("node_modules").resolve("pkg"));
		Files.writeString(pkg.resolve("SKILL.md"), "---\nname: pkg\n---\n");

		assertTrue(checks(validator.validate(skill), "nested_manifest").isEmpty());
	}

	@ParameterizedTest
	@MethodSource("triggerCases")
	void descriptionTriggerWindow(String description, boolean expected) throws IOException {
		Path skill = writeSkill(tmp.resolve("skills"), "portable-skill", "name: portable-skill\ndescription: "
				+ description + "\n" + "metadata:\n  author: Test User <testuser@example.com>\n", CLEAN_BODY);

		List<Finding> findings = checks(validator.validate(skill), "description_trigger");

		assertEquals(expected, !findings.isEmpty());
		assertTrue(findings.stream().allMatch(f -> f.getSeverity() == Severity.LOW));
	}

	static Stream<Arguments> triggerCases() {
		return Stream.of(Arguments.of("Use when checking harness portability of a skill.", false),
				Arguments.of("Formats tables. Use when the user asks for a table.", false),
				Arguments.of("Use this skill to format tables in reports.", false),
				Arguments.of("Formats tables, charts and other structured report content nicely. Use when needed.",
						true),
				Arguments.of("Formats tables in reports.", true),
				Arguments.of("Formats tables, charts and structured report sections. Use when needed.", true));
	}

	@Test
	void descriptionOverUtf16LimitIsMedium() throws IOException {
		String description = "Use when testing. " + "\uD83D\uDE00".repeat(600);
		Path skill = writeSkill(tmp.resolve("skills"), "portable-skill", "name: portable-skill\ndescription: "
				+ description + "\n" + "metadata:\n  author: Test User <testuser@example.com>\n", CLEAN_BODY);

		ValidationResult result = validator.validate(skill);
		List<Finding> findings = checks(result, "description_utf16_length");

		assertTrue(result.isPassed(), () -> result.getErrors().toString());
		assertEquals(List.of(600 * 2 + "Use when testing. ".length()),
				findings.stream().map(f -> f.getMetadata().get("utf16_units")).toList());
	}

	@Test
	void unknownTopLevelFieldsAreLow() throws IOException {
		Path skill = writeSkill(tmp.resolve("skills"), "portable-skill",
				"name: portable-skill\ndescription: Use when checking harness portability of a skill.\n"
						+ "metadata:\n  author: Test User <testuser@example.com>\n"
						+ "owner: team-x\nversion: 1.0.0\nlicense: MIT\nallowed-tools: Read\n",
				CLEAN_BODY);

		ValidationResult result = validator.validate(skill);
		List<Finding> findings = checks(result, "unknown_field");

		assertTrue(result.isPassed(), () -> result.getErrors().toString());
		assertEquals(List.of(List.of("owner", "version")),
				findings.stream().map(f -> f.getMetadata().get("fields")).toList());
	}

	// ------------------------------------------------------------------
	// Folder structure
	// ------------------------------------------------------------------

	@Test
	void folderWithoutStandardDirsIsAdvisory() throws IOException {
		Path root = Files.createDirectories(tmp.resolve("loose"));
		writeCleanSkill(root, "loose-skill");

		ValidationResult result = validator.validate(root.resolve("loose-skill"));

		assertTrue(result.isPassed(), () -> result.getErrors().toString());
		List<Finding> findings = checks(result, "folder_hierarchy");
		assertEquals(1, findings.size());
		assertEquals(Severity.MEDIUM, findings.get(0).getSeverity());
		assertTrue(findings.get(0).getMessage().contains("Skill not in standard location"));
	}

	@Test
	void folderScanReportsStructure() throws IOException {
		Path skills = Files.createDirectories(tmp.resolve("skills"));
		writeCleanSkill(skills, "one-skill");
		writeCleanSkill(skills, "two-skill");

		ValidationResult result = validator.validate(tmp);

		assertTrue(result.isPassed(), () -> result.getErrors().toString());
		assertEquals(2, result.getSummary().getFilesScanned());
		assertNotNull(detail(result, "folder_structure"));
		assertEquals(2, detail(result, "skills_directory").getMetadata().get("item_count"));
	}

	// ------------------------------------------------------------------
	// Line count
	// ------------------------------------------------------------------

	@Test
	void lineCountIsAdvisory() throws IOException {
		StringBuilder body = new StringBuilder(CLEAN_BODY);
		for (int i = 0; i < 600; i++) {
			body.append("filler line\n");
		}
		Path skill = writeSkill(tmp.resolve("skills"), "long-skill",
				"name: long-skill\ndescription: Use when checking the line count of a long manifest.\n"
						+ "metadata:\n  author: Test User <testuser@example.com>\n",
				body.toString());

		ValidationResult result = validator.validate(skill);

		assertTrue(result.isPassed(), () -> result.getErrors().toString());
		List<Finding> findings = checks(result, "line_count");
		assertEquals(1, findings.size());
		assertEquals(Severity.MEDIUM, findings.get(0).getSeverity());
		assertTrue(findings.get(0).getMessage().startsWith("SKILL.md has 6"), findings.get(0).getMessage());
	}

	@Test
	void splitLinesMatchesPython() {
		assertEquals(0, SchemaValidator.pySplitLinesCount(""));
		assertEquals(1, SchemaValidator.pySplitLinesCount("a"));
		assertEquals(1, SchemaValidator.pySplitLinesCount("a\n"));
		assertEquals(2, SchemaValidator.pySplitLinesCount("a\nb"));
		assertEquals(2, SchemaValidator.pySplitLinesCount("a\nb\n"));
		assertEquals(2, SchemaValidator.pySplitLinesCount("a\r\nb"));
	}

	@Test
	void manifestRecordIsStoredOnSuccess() throws IOException {
		Path skill = writeCleanSkill(tmp.resolve("skills"), "portable-skill");

		ValidationResult result = validator.validate(skill);

		assertTrue(result.isPassed(), () -> result.getErrors().toString());
		SchemaValidator.SchemaManifest manifest = (SchemaValidator.SchemaManifest) result.getMetadata().get("manifest");
		assertNotNull(manifest);
		assertEquals(skill.resolve("SKILL.md").toString(), manifest.filePath());
		assertEquals(SchemaValidator.pySplitLinesCount(
				Files.readString(Path.of(manifest.filePath()), StandardCharsets.UTF_8)), manifest.lineCount());
		assertTrue(manifest.content().startsWith("\n# Clean Skill"));
	}

	@Test
	void nameAndDescriptionLengthsCountCodePoints() throws IOException {
		String emojiDescription = "\uD83D\uDE00".repeat(520); // 520 characters, 1040
																// UTF-16 units
		Path skill = writeSkill(tmp.resolve("skills"), "emoji-desc",
				"name: emoji-desc\ndescription: \"" + emojiDescription + "\"\n", CLEAN_BODY);

		ValidationResult result = validator.validate(skill);

		assertTrue(
				checks(result, "frontmatter_field").stream()
					.noneMatch(f -> f.getMessage().contains("at most 1024 characters")),
				() -> checks(result, "frontmatter_field").toString());
		// The same value still trips the UTF-16-aware Copilot CLI advisory.
		assertEquals(1, checks(result, "description_utf16_length").size());
	}

	@Test
	void unquotedSupplementaryCharactersStillParse() throws IOException {
		// Regression guard: SnakeYAML's reader over a String throws when a plain
		// (unquoted) scalar's supplementary characters straddle its internal buffer.
		// Frontmatter is handed to it as UTF-8 bytes so this parses the way PyYAML
		// parses it and only the UTF-16 advisory remains.
		String emojiDescription = "Use when testing emoji. " + "\uD83D\uDE00".repeat(600);
		Path skill = writeSkill(tmp.resolve("skills"), "emoji-plain",
				"name: emoji-plain\ndescription: " + emojiDescription + "\n", CLEAN_BODY);

		ValidationResult result = validator.validate(skill);

		assertTrue(checks(result, "yaml_syntax").isEmpty(), () -> checks(result, "yaml_syntax").toString());
		assertTrue(checks(result, "frontmatter_field").isEmpty(), () -> checks(result, "frontmatter_field").toString());
		List<Finding> utf16 = checks(result, "description_utf16_length");
		assertEquals(1, utf16.size());
		assertEquals(Severity.MEDIUM, utf16.get(0).getSeverity());
	}

	@Test
	void byteOrderMarkKeepsFrontmatterLineNumbersAligned() throws IOException {
		Path skill = writeSkill(tmp.resolve("skills"), "bom-skill",
				"name: bom-skill\ndescription: Use when checking BOM handling in a manifest.\n"
						+ "metadata:\n  author: Test User <testuser@example.com>\n" + "description: Second value.\n",
				CLEAN_BODY);
		Path manifest = skill.resolve("SKILL.md");
		byte[] raw = Files.readAllBytes(manifest);
		byte[] withBom = new byte[raw.length + 3];
		System.arraycopy(new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF }, 0, withBom, 0, 3);
		System.arraycopy(raw, 0, withBom, 3, raw.length);
		Files.write(manifest, withBom);

		ValidationResult result = validator.validate(skill);

		assertTrue(checks(result, "frontmatter_format").isEmpty());
		assertEquals(6, checks(result, "duplicate_key").get(0).getLineNumber().intValue());
	}

}
