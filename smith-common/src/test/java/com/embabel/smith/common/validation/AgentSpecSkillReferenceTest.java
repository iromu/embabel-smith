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
import java.nio.file.StandardOpenOption;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the skill-carrying half of the format: an optional {@code skills/} directory,
 * the four layout cases (none, the pack's own, one shared with the folder the pack sits
 * in, and an own copy shadowing a shared name), and the rule that a declared reference
 * naming no carried skill is refused before deploy.
 * <p>
 * The reference checks are filesystem checks — a skill is a directory, not a field — so
 * they run through {@link AgentSpecValidator#validate(Path)} rather than the in-memory
 * {@code validate(AgentSpec)}.
 *
 * @author Ivan Rodriguez
 */
class AgentSpecSkillReferenceTest {

	private final AgentSpecValidator validator = new AgentSpecValidator();

	/** The skill the packs below carry, spelled as its front matter declares it. */
	private static final String SKILL = "repo-notes";

	/** The same skill misspelled with an underscore, as a step's author might type it. */
	private static final String MISSPELLED_SKILL = "repo_notes";

	// --- the four layout cases ------------------------------------------------

	@Test
	void acceptsAPackThatCarriesNoSkillsDirectory(@TempDir Path dir) throws Exception {
		// The "unchanged from today" case: a pack that neither carries skills nor asks
		// for
		// any validates exactly as it did before the directory existed.
		write(packDeclaring(List.of()), dir);

		assertThat(validator.validate(dir).isValid()).isTrue();
		assertThat(validator.carriedSkills(dir)).isEmpty();
	}

	@Test
	void acceptsAPackCarryingTheSkillItReferences(@TempDir Path dir) throws Exception {
		write(packDeclaring(List.of(SKILL)), dir);
		skill(dir.resolve("skills"), SKILL);

		assertThat(validator.validate(dir).isValid()).isTrue();
		assertThat(validator.carriedSkills(dir)).containsKey(SKILL);
		assertThat(validator.carriedSkills(dir).get(SKILL)).isEqualTo(dir.resolve("skills/" + SKILL));
	}

	@Test
	void acceptsASkillSharedByTheFolderThePackSitsIn(@TempDir Path root) throws Exception {
		// Several agent definitions side by side with one skills/ beside them: the shared
		// set
		// is the pack's immediate parent, so an agent carrying nothing of its own
		// resolves.
		Path packDir = root.resolve("an-agent");
		write(packDeclaring(List.of(SKILL)), packDir);
		skill(root.resolve("skills"), SKILL);

		assertThat(validator.validate(packDir).isValid()).isTrue();
		assertThat(validator.carriedSkills(packDir).get(SKILL)).isEqualTo(root.resolve("skills/" + SKILL));
	}

	@Test
	void prefersThePackOwnCopyOverASharedName(@TempDir Path root) throws Exception {
		Path packDir = root.resolve("an-agent");
		write(packDeclaring(List.of(SKILL)), packDir);
		Path shared = skill(root.resolve("skills"), SKILL);
		Path own = skill(packDir.resolve("skills"), SKILL);

		assertThat(validator.validate(packDir).isValid()).isTrue();
		// The report says which folder answered, which is the only way a reader can tell
		// a
		// shadow from a fallback.
		assertThat(validator.carriedSkills(packDir).get(SKILL)).isEqualTo(own);
		assertThat(validator.carriedSkills(packDir).get(SKILL)).isNotEqualTo(shared);
	}

	// --- a folder that is not a skill ------------------------------------------

	@Test
	void reportsASkillsFolderThatCarriesNoSkillDefinition(@TempDir Path dir) throws Exception {
		write(packDeclaring(List.of()), dir);
		Files.createDirectories(dir.resolve("skills/" + SKILL));

		ValidationResult result = validator.validate(dir);

		assertThat(result.isValid()).isFalse();
		List<ValidationIssue> issues = result.issuesWithCode("SKILL_MISSING_DEFINITION");
		assertThat(issues).hasSize(1);
		assertThat(issues.get(0).message()).contains(SKILL);
		assertThat(issues.get(0).location()).isEqualTo("skills/" + SKILL);
		// It is not carried, so it cannot answer a reference either.
		assertThat(validator.carriedSkills(dir)).isEmpty();
	}

	@Test
	void carriesASkillByTheNameItsFrontMatterDeclares(@TempDir Path dir) throws Exception {
		// A step names the declared skill rather than the folder, so the declared name is
		// what has to resolve.
		write(packDeclaring(List.of(SKILL)), dir);
		Path folder = dir.resolve("skills/repo_notes_folder");
		Files.createDirectories(folder);
		Files.writeString(folder.resolve("SKILL.md"),
				"---\nname: " + SKILL + "\ndescription: Notes\n---\n\nRead the repo.\n");

		assertThat(validator.validate(dir).isValid()).isTrue();
		assertThat(validator.carriedSkills(dir).get(SKILL)).isEqualTo(folder);
	}

	// --- dangling references ---------------------------------------------------

	@Test
	void reportsADeclaredReferenceWithNoSkillsFolderAsDangling(@TempDir Path dir) throws Exception {
		// The declaration survives the read even though nothing carries it, so it is
		// reported
		// rather than dropped: a dropped declaration reads as a capability that never
		// existed.
		write(packDeclaring(List.of(SKILL)), dir);

		ValidationResult result = validator.validate(dir);

		assertThat(result.isValid()).isFalse();
		List<ValidationIssue> issues = result.issuesWithCode("DANGLING_REFERENCE");
		assertThat(issues).hasSize(2);
		assertThat(issues).allMatch((issue) -> issue.message().contains(SKILL));
		assertThat(issues).anyMatch((issue) -> issue.location().equals("steps/noteItDown.yml"));
	}

	@Test
	void rejectsAReferenceToASkillThePackDoesNotCarry(@TempDir Path dir) throws Exception {
		write(packDeclaring(List.of("another-skill")), dir);
		skill(dir.resolve("skills"), SKILL);

		ValidationResult result = validator.validate(dir);

		assertThat(result.isValid()).isFalse();
		List<ValidationIssue> issues = result.issuesWithCode("DANGLING_REFERENCE");
		assertThat(issues).hasSize(2);
		// The carried name is named too, so the reader sees what the pack does answer to.
		assertThat(issues)
			.allMatch((issue) -> issue.message().contains("another-skill") && issue.message().contains(SKILL));
	}

	@Test
	void reportsOnlyTheStepWhoseSpellingIsWrong(@TempDir Path dir) throws Exception {
		// One step spells the shared skill right, one spells it with an underscore.
		write(pack(List.of(SKILL), List.of(MISSPELLED_SKILL)), dir);
		skill(dir.resolve("skills"), SKILL);

		ValidationResult result = validator.validate(dir);

		assertThat(result.isValid()).isFalse();
		List<ValidationIssue> issues = result.issuesWithCode("DANGLING_REFERENCE");
		assertThat(issues).hasSize(1);
		assertThat(issues.get(0).message()).contains("spellCheck").contains(MISSPELLED_SKILL);
		assertThat(issues.get(0).location()).isEqualTo("steps/spellCheck.yml");
		assertThat(result.issues()).noneMatch((issue) -> issue.message().contains("noteItDown"));
	}

	@Test
	void stopsTheSharedSetAtTheImmediateParent(@TempDir Path root) throws Exception {
		// Two levels up is not shared: the immediate parent is also the source root when
		// a
		// source points at a folder of packs, so consulting ancestors would make one
		// stray
		// skills/ global to every pack under it.
		Path packDir = root.resolve("group/an-agent");
		write(packDeclaring(List.of(SKILL)), packDir);
		skill(root.resolve("skills"), SKILL);

		ValidationResult result = validator.validate(packDir);

		assertThat(result.isValid()).isFalse();
		List<ValidationIssue> issues = result.issuesWithCode("DANGLING_REFERENCE");
		assertThat(issues).hasSize(2);
		assertThat(issues).allMatch((issue) -> issue.message().contains(SKILL));
	}

	@Test
	void ignoresAReferencesKeyOnTheGoalFile(@TempDir Path dir) throws Exception {
		// Upstream's GoalSpec carries no references field, and a goal contributes no
		// model
		// call of its own, so the key is inert rather than an error: the pack still
		// validates.
		write(packDeclaring(List.of()), dir);
		Files.writeString(dir.resolve("goal.yml"),
				Files.readString(dir.resolve("goal.yml")) + "references:\n  - " + SKILL + "\n",
				StandardOpenOption.APPEND);

		assertThat(validator.validate(dir).isValid()).isTrue();
	}

	// --- fixtures --------------------------------------------------------------

	private static void write(AgentSpec pack, Path dir) throws IOException {
		new AgentSpecWriter().write(pack, dir);
	}

	private static Path skill(Path skillsDir, String name) throws IOException {
		Path folder = skillsDir.resolve(name);
		Files.createDirectories(folder);
		Files.writeString(folder.resolve("SKILL.md"),
				"---\nname: " + name + "\ndescription: What the skill does\n---\n\nDo the thing.\n");
		return folder;
	}

	/** A pack whose two steps both declare the same references. */
	private static AgentSpec packDeclaring(List<String> references) {
		return pack(references, references);
	}

	/** A pack whose steps declare their own reference lists. */
	private static AgentSpec pack(List<String> noteReferences, List<String> spellReferences) {
		GoalStepSpec goal = new GoalStepSpec("goal", "notesTaken", "Notes have been taken",
				SampleAgentSpecs.BACKGROUND_MESSAGE, true);
		ActionStepSpec note = step("noteItDown", "Take notes down", noteReferences);
		ActionStepSpec spell = step("spellCheck", "Spell-check the notes", spellReferences);
		return new AgentSpec(new AgentSpecManifest("notes", "1.0.0"), goal, List.of(note, spell), List.of());
	}

	private static ActionStepSpec step(String name, String description, List<String> references) {
		return new ActionStepSpec(ActionStepSpec.STEP_TYPE, name, description, List.of(SampleAgentSpecs.USER_INPUT),
				SampleAgentSpecs.BACKGROUND_MESSAGE, "Work on: {{userInput.content}}", null, null, null, null, null,
				null, null, references);
	}

}
