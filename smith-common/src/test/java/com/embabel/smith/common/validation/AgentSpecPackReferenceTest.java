/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.common.validation;

import com.embabel.smith.common.io.AgentSpecWriter;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.samples.SampleAgentSpecs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The pack-carried half of the reference checks: what a {@code references.yml} may
 * declare (only the kinds the runtime can construct), that its backing path has to exist,
 * that two names sanitizing to one tool prefix are refused rather than silently
 * overwritten, and that a {@code references:} written in {@code goal.yml} is inert —
 * reported, and the pack still validates, because a goal contributes no model call of its
 * own.
 * <p>
 * Every check runs both ways: a correctly declared reference leaves the pack clean, a
 * mistake reports the one thing that is wrong.
 *
 * @author Ivan Rodriguez
 */
class AgentSpecPackReferenceTest {

	private final AgentSpecValidator validator = new AgentSpecValidator();

	/** The name the document reference below declares, as a step would name it. */
	private static final String DOC = "domain-guide";

	// --- a correctly declared reference stays clean ---------------------------

	@Test
	void acceptsADocumentReferenceThatExists(@TempDir Path dir) throws Exception {
		write(packDeclaring(List.of(DOC)), dir);
		referencesYml(dir, "- name: " + DOC + "\n  kind: document\n  path: references/domain.md\n");
		Files.createDirectories(dir.resolve("references"));
		Files.writeString(dir.resolve("references/domain.md"), "# The guide\n");

		ValidationResult result = validator.validate(dir);

		assertThat(apartFromRewriteNote(result)).isEmpty();
		assertThat(result.isValid()).isTrue();
	}

	@Test
	void acceptsADirectoryAndABriefReference(@TempDir Path dir) throws Exception {
		// A brief is literal text, so it is the one kind with no path to check — refusing
		// it
		// for lacking one would be the check over-firing.
		write(packDeclaring(List.of("notes-dir", "run-brief")), dir);
		Files.createDirectories(dir.resolve("notes"));
		referencesYml(dir,
				"- name: notes-dir\n  kind: directory\n  path: notes\n" + "- name: run-brief\n  kind: brief\n");

		assertThat(apartFromRewriteNote(validator.validate(dir))).isEmpty();
	}

	@Test
	void acceptsAReferencesFileCarryingNothing(@TempDir Path dir) throws Exception {
		write(packDeclaring(List.of()), dir);
		referencesYml(dir, "references: []\n");

		assertThat(validator.validate(dir).isValid()).isTrue();
	}

	// --- a kind the runtime cannot construct ----------------------------------

	@Test
	void refusesAReferenceKindTheRuntimeCannotConstruct(@TempDir Path dir) throws Exception {
		write(packDeclaring(List.of(DOC)), dir);
		referencesYml(dir, "- name: " + DOC + "\n  kind: webhook\n  path: references/domain.md\n");

		ValidationResult result = validator.validate(dir);

		assertThat(result.isValid()).isFalse();
		List<ValidationIssue> issues = result.issuesWithCode("REFERENCE_UNKNOWN_KIND");
		assertThat(issues).hasSize(1);
		// The kind is named, as are the kinds that would be accepted, so the author does
		// not
		// have to go looking for the vocabulary.
		assertThat(issues.get(0).message()).contains("webhook").contains("document").contains(DOC);
	}

	// --- the scripts switch -------------------------------------------------

	@Test
	void acceptsScriptsOnASkillsReference(@TempDir Path dir) throws Exception {
		// The one switch a references.yml entry may add. It names no class and no
		// property,
		// only which shape of the folder the entry already gave the runtime should build,
		// so a pack asking for it is what the format allows rather than something to warn
		// about.
		write(packDeclaring(List.of("running")), dir);
		Files.createDirectories(dir.resolve("carried-skills"));
		referencesYml(dir, "- name: running\n  kind: skills\n  path: carried-skills\n  scripts: true\n");

		ValidationResult result = validator.validate(dir);

		assertThat(apartFromRewriteNote(result)).isEmpty();
		assertThat(result.issuesWithCode("REFERENCE_SCRIPTS_UNSUPPORTED")).isEmpty();
		assertThat(result.isValid()).isTrue();
	}

	@Test
	void refusesScriptsOnAKindThatHandsOutNoScriptTool(@TempDir Path dir) throws Exception {
		// A directory reference has file tools and no script tool, so the switch would
		// read
		// as a capability the pack configured while doing nothing — the one failure mode
		// the
		// format's invariants exist to prevent.
		write(packDeclaring(List.of("notes-dir")), dir);
		Files.createDirectories(dir.resolve("notes"));
		referencesYml(dir, "- name: notes-dir\n  kind: directory\n  path: notes\n  scripts: true\n");

		ValidationResult result = validator.validate(dir);

		assertThat(result.isValid()).isFalse();
		List<ValidationIssue> issues = result.issuesWithCode("REFERENCE_SCRIPTS_UNSUPPORTED");
		assertThat(issues).hasSize(1);
		assertThat(issues.get(0).message()).contains("notes-dir").contains("directory").contains("scripts");
	}

	// --- a backing path that is gone ------------------------------------------

	@Test
	void refusesAReferenceWhoseBackingPathIsGone(@TempDir Path dir) throws Exception {
		write(packDeclaring(List.of(DOC)), dir);
		referencesYml(dir, "- name: " + DOC + "\n  kind: document\n  path: references/domain.md\n");

		ValidationResult result = validator.validate(dir);

		assertThat(result.isValid()).isFalse();
		List<ValidationIssue> issues = result.issuesWithCode("REFERENCE_MISSING_PATH");
		assertThat(issues).hasSize(1);
		assertThat(issues.get(0).message()).contains("references/domain.md").contains(DOC);
	}

	@Test
	void refusesADirectoryReferencePointingAtAFile(@TempDir Path dir) throws Exception {
		// The path exists, so a bare existence check would pass this one: a directory
		// kind
		// needs a directory, because that is the shape the runtime constructs.
		write(packDeclaring(List.of("notes-dir")), dir);
		Files.createDirectories(dir.resolve("references"));
		Files.writeString(dir.resolve("notes"), "not a directory\n");
		referencesYml(dir, "- name: notes-dir\n  kind: directory\n  path: notes\n");

		assertThat(validator.validate(dir).issuesWithCode("REFERENCE_MISSING_PATH")).hasSize(1);
	}

	@Test
	void refusesAPathBackedReferenceThatDeclinesNoPath(@TempDir Path dir) throws Exception {
		write(packDeclaring(List.of(DOC)), dir);
		referencesYml(dir, "- name: " + DOC + "\n  kind: document\n");

		assertThat(validator.validate(dir).issuesWithCode("REFERENCE_MISSING_PATH")).hasSize(1);
	}

	// --- names that sanitize to one prefix ------------------------------------

	@Test
	void refusesTwoNamesThatSanitizeToOneToolPrefix(@TempDir Path dir) throws Exception {
		// The platform's prefix is the name with every non-alphanumeric character
		// replaced
		// and lowercased, so these two are one prefix and one reference's tools would
		// overwrite the other's in a plain map put.
		write(packDeclaring(List.of("repo-notes", "repo.notes")), dir);
		referencesYml(dir, "- name: repo-notes\n  kind: brief\n" + "- name: repo.notes\n  kind: brief\n");

		ValidationResult result = validator.validate(dir);

		assertThat(result.isValid()).isFalse();
		List<ValidationIssue> issues = result.issuesWithCode("REFERENCE_PREFIX_COLLISION");
		assertThat(issues).hasSize(1);
		assertThat(issues.get(0).message()).contains("repo-notes").contains("repo.notes").contains("repo_notes");
	}

	@Test
	void refusesAReferenceNameThatCollidesWithACarriedSkill(@TempDir Path dir) throws Exception {
		write(packDeclaring(List.of("repo-notes")), dir);
		skill(dir.resolve("skills"), "repo-notes");
		referencesYml(dir, "- name: repo.notes\n  kind: brief\n");

		ValidationResult result = validator.validate(dir);

		assertThat(result.isValid()).isFalse();
		List<ValidationIssue> issues = result.issuesWithCode("REFERENCE_PREFIX_COLLISION");
		assertThat(issues).hasSize(1);
		assertThat(issues.get(0).message()).contains("skill 'repo-notes'").contains("repo.notes");
	}

	@Test
	void refusesAReferenceNameThatCannotBecomeAPrefixAtAll(@TempDir Path dir) throws Exception {
		write(packDeclaring(List.of("--")), dir);
		referencesYml(dir, "- name: \"--\"\n  kind: brief\n");

		ValidationResult result = validator.validate(dir);

		assertThat(result.isValid()).isFalse();
		assertThat(result.issuesWithCode("REFERENCE_UNUSABLE_NAME")).hasSize(1);
	}

	// --- a declared name that nothing carries -------------------------------

	@Test
	void stillRefusesAStepNamingSomethingNeitherSkillsNorReferencesDeclare(@TempDir Path dir) throws Exception {
		write(packDeclaring(List.of("typo-guide")), dir);
		referencesYml(dir, "- name: " + DOC + "\n  kind: brief\n");

		ValidationResult result = validator.validate(dir);

		assertThat(result.isValid()).isFalse();
		List<ValidationIssue> issues = result.issuesWithCode("DANGLING_REFERENCE");
		assertThat(issues).hasSize(2);
		// What the pack does carry is named, so the reader sees the near miss.
		assertThat(issues).allMatch((i) -> i.message().contains(DOC) && i.message().contains("typo-guide"));
	}

	// --- a goal that declares references --------------------------------------

	@Test
	void reportsAReferencesDeclarationOnAGoalAsInertAndStillValidatesThePack(@TempDir Path dir) throws Exception {
		// Upstream's GoalSpec carries no references field and a goal contributes no model
		// call
		// of its own, so the declaration is ignored rather than wrong: refusing the pack
		// for
		// it would be the check over-firing. What the check buys is the statement.
		write(packDeclaring(List.of()), dir);
		appendToGoal(dir, "references:\n  - " + DOC + "\n");

		ValidationResult result = validator.validate(dir);

		assertThat(result.isValid()).as(() -> result.issues().toString()).isTrue();
		List<ValidationIssue> issues = result.issuesWithCode("REFERENCES_INERT_ON_GOAL");
		assertThat(issues).hasSize(1);
		assertThat(issues.get(0).message()).contains("references").contains("ignored");
		assertThat(issues.get(0).location()).isEqualTo("goal.yml");
		assertThat(issues.get(0).severity()).isEqualTo(ValidationIssue.Severity.ADVISORY);
	}

	@Test
	void reportsNothingOnAGoalThatDeclaresNoReferences(@TempDir Path dir) throws Exception {
		// The other direction: a goal written the way the format means it is silent, so
		// the
		// advisory above cannot be a constant.
		write(packDeclaring(List.of()), dir);

		assertThat(validator.validate(dir).issuesWithCode("REFERENCES_INERT_ON_GOAL")).isEmpty();
	}

	@Test
	void reportsAnActionOnlyKeyOnAGoalAsInert(@TempDir Path dir) throws Exception {
		write(packDeclaring(List.of()), dir);
		appendToGoal(dir, "toolGroups:\n  - file\n");

		ValidationResult result = validator.validate(dir);

		assertThat(result.isValid()).as(() -> result.issues().toString()).isTrue();
		assertThat(result.issuesWithCode("KEY_INERT_ON_GOAL")).hasSize(1);
	}

	// --- a references file the writer does not carry --------------------------

	@Test
	void reportsThatAReferencesFileWillNotSurviveARewrite(@TempDir Path dir) throws Exception {
		// The pack deploys fine — the runtime reads this file straight off disk. What it
		// cannot survive is a rewrite: AgentSpecWriter emits manifest.yml, goal.yml,
		// steps/ and types/ and has no representation for a references file, so a studio
		// save or a builder repair ships the pack with its references gone. The format
		// ruled out giving the writer a passthrough, so the choice under test is that the
		// loss is said out loud instead of being silent.
		write(packDeclaring(List.of(DOC)), dir);
		referencesYml(dir, "- name: " + DOC + "\n  kind: brief\n");

		ValidationResult result = validator.validate(dir);

		assertThat(result.isValid()).as(() -> result.issues().toString()).isTrue();
		List<ValidationIssue> issues = result.issuesWithCode("REFERENCES_FILE_NOT_PRESERVED_ON_REWRITE");
		assertThat(issues).hasSize(1);
		assertThat(issues.get(0).severity()).isEqualTo(ValidationIssue.Severity.ADVISORY);
		assertThat(issues.get(0).location()).isEqualTo("references.yml");
		assertThat(issues.get(0).message()).contains("writer").contains("drop");
	}

	@Test
	void saysNothingAboutRewritesWhenThePackCarriesNoReferencesFile(@TempDir Path dir) throws Exception {
		// The other direction, and the reason the note above is not a constant: a pack
		// whose references are skills alone has nothing for the writer to lose.
		write(packDeclaring(List.of()), dir);

		assertThat(validator.validate(dir).issuesWithCode("REFERENCES_FILE_NOT_PRESERVED_ON_REWRITE")).isEmpty();
	}

	// --- the prefix itself ---------------------------------------------------

	@Test
	void sanitizesAReferenceNameTheWayThePlatformDoes() {
		assertThat(AgentSpecValidator.toolPrefix("repo-notes")).isEqualTo("repo_notes");
		assertThat(AgentSpecValidator.toolPrefix("Repo.Notes")).isEqualTo("repo_notes");
		assertThat(AgentSpecValidator.toolPrefix("repo notes")).isEqualTo("repo_notes");
	}

	// --- fixtures ------------------------------------------------------------

	private static void write(AgentSpec pack, Path dir) throws IOException {
		new AgentSpecWriter().write(pack, dir);
	}

	private static void referencesYml(Path dir, String entries) throws IOException {
		Files.writeString(dir.resolve("references.yml"), "references:\n" + entries);
	}

	/**
	 * The findings a pack carries apart from the rewrite note, which any pack with a
	 * {@code references.yml} earns whatever else it gets right. It is a fact about the
	 * writer rather than about the declaration a test here is about, so the declaration
	 * tests read it out; the note itself is pinned by
	 * {@link #reportsThatAReferencesFileWillNotSurviveARewrite}.
	 * @param result the validation outcome
	 * @return the issues left once the rewrite note is set aside
	 */
	private static List<ValidationIssue> apartFromRewriteNote(ValidationResult result) {
		return result.issues()
			.stream()
			.filter((issue) -> !"REFERENCES_FILE_NOT_PRESERVED_ON_REWRITE".equals(issue.code()))
			.toList();
	}

	/**
	 * Adds keys to the goal file as written. The writer emits one YAML document, so an
	 * append lands in that document — which is what the runtime's verbatim staging hands
	 * the upstream reader.
	 */
	private static void appendToGoal(Path dir, String lines) throws IOException {
		Files.writeString(dir.resolve("goal.yml"), Files.readString(dir.resolve("goal.yml")) + lines);
	}

	private static void skill(Path skillsDir, String name) throws IOException {
		Path folder = skillsDir.resolve(name);
		Files.createDirectories(folder);
		Files.writeString(folder.resolve("SKILL.md"),
				"---\nname: " + name + "\ndescription: What the skill does\n---\n\nDo the thing.\n");
	}

	/** A pack whose two steps both declare the same references. */
	private static AgentSpec packDeclaring(List<String> references) {
		GoalStepSpec goal = new GoalStepSpec("goal", "workDone", "The work is done",
				SampleAgentSpecs.BACKGROUND_MESSAGE, true);
		return new AgentSpec(new AgentSpecManifest("references-demo", "1.0.0"), goal,
				List.of(step("noteItDown", references), step("spellCheck", references)), List.of());
	}

	private static ActionStepSpec step(String name, List<String> references) {
		return new ActionStepSpec(ActionStepSpec.STEP_TYPE, name, "Do the thing", List.of(SampleAgentSpecs.USER_INPUT),
				SampleAgentSpecs.BACKGROUND_MESSAGE, "Work on: {{userInput.content}}", null, null, null, null, null,
				null, null, references);
	}

}
