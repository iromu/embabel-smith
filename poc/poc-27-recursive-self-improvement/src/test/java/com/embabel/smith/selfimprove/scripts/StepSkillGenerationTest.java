/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.scripts;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.embabel.agent.skills.script.ScriptLanguage;
import com.embabel.smith.builder.skill.SkillMdValidator;
import com.embabel.smith.builder.skill.SkillPrompts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 6.1, offline: one action step of a generated process becomes a skill folder whose
 * frontmatter the product's own gate accepts and whose {@code scripts/} folder carries a
 * JavaScript file.
 *
 * <p>
 * The gate used is {@link SkillMdValidator} itself, not a paraphrase of its rules, so the
 * claim is about the artifact the product would judge rather than about what this section
 * believes the Agent Skills spec says. The control case is what makes the empty verdict
 * mean something: the same validator run over a name this section's slug could not have
 * produced does report violations, so "no issues" is a result and not a check that cannot
 * fail.
 *
 * <p>
 * The folder is read back from disk rather than trusted from the record that generated
 * it. A {@code scripts/} folder that exists only in memory is not evidence about a
 * candidate.
 *
 * @author Ivan Rodriguez
 */
class StepSkillGenerationTest {

	@TempDir
	Path workDir;

	private final ScriptFixtures.Canned caller = new ScriptFixtures.Canned()
		.answer(SkillPrompts.generateSkill(),
				ScriptFixtures.skillAnswer(
						"Scores a draft against the frozen task set and reports its pass rate, so a builder "
								+ "run knows which tasks it missed.",
						"Read the draft, compare it with each frozen task, and report the pass rate."))
		.answer(StepSkillGenerator.SCRIPT_PROMPT, ScriptFixtures.scriptAnswer("""
				const draft = stdin.trim();
				if (!draft) {
				  console.error('no draft on stdin');
				  process.exit(2);
				}
				console.log('scored ' + draft.length + ' characters');
				"""));

	private StepSkillGenerator.Generated generate() {
		return new StepSkillGenerator(this.caller).generate(ScriptFixtures.AGENT, ScriptFixtures.STEP, this.workDir);
	}

	@Test
	void theGeneratedSkillClearsTheProductFrontmatterGate() {
		StepSkillGenerator.Generated skill = generate();

		assertThat(skill.frontmatterIssues()).as(() -> skill.skillName() + " -> " + skill.frontmatterIssues())
			.isEmpty();

		// Control: the gate is not vacuous. This is the shape a name would have if the
		// slug did not flatten it, and the validator reports it. Without this, an empty
		// list could just mean the check never looks at anything.
		assertThat(SkillMdValidator.validate("Score A Draft", "")).isNotEmpty();
	}

	@Test
	void theFolderCarriesTheSkillMdAndAJavaScriptScript() throws IOException {
		StepSkillGenerator.Generated skill = generate();

		assertThat(skill.scriptFiles()).as("the scripts/ folder of " + skill.skillDir()).isNotEmpty();
		assertThat(skill.scriptFiles()).allMatch((name) -> name.endsWith(".js"));
		assertThat(Files.readString(skill.skillDir().resolve("SKILL.md"), StandardCharsets.UTF_8))
			.contains("name: " + skill.skillName());

		// The folder is named after the skill, which is the rule the Agent Skills format
		// is built on and the reason the name has to be a slug in the first place.
		assertThat(skill.skillDir().getFileName().toString()).isEqualTo(skill.skillName());
		assertThat(Files.readString(skill.skillDir().resolve("scripts").resolve(skill.scriptFileName()),
				StandardCharsets.UTF_8))
			.contains("console.log");
	}

	@Test
	void theSkillIsGeneratedFromTheStepsOwnDeclaration() {
		generate();

		// The skill half is asked with the product's own skill prompt, in call order, so
		// the frontmatter this produces is the artifact SkillBuilderService's gate
		// judges.
		assertThat(this.caller.systems()).containsExactly(SkillPrompts.generateSkill(),
				StepSkillGenerator.SCRIPT_PROMPT);

		String asked = this.caller.users().get(0);
		assertThat(asked).contains("score-draft")
			.contains("ScoredDraft")
			.contains("Compare {{ draftedSpec }} against every frozen task");
		// And the script half is asked with the same step, so the two halves of the skill
		// describe the same work rather than two guesses about it.
		assertThat(this.caller.users().get(1)).contains("score-draft").contains("DraftedSpec");
	}

	@Test
	void theScriptIsAddressableTheWayEmbabelAddressesASkillScript() {
		StepSkillGenerator.Generated skill = generate();

		assertThat(skill.script(skill.scriptFileName()).getScriptPath())
			.isEqualTo(skill.skillDir().resolve("scripts").resolve(skill.scriptFileName()));
		assertThat(skill.script(skill.scriptFileName()).getSkillName()).isEqualTo(skill.skillName());
		assertThat(skill.script(skill.scriptFileName()).getLanguage()).isEqualTo(ScriptLanguage.JAVASCRIPT);

		// The handle the engine takes points at a file that exists, which is what the
		// engine's own pre-flight check requires before it will run anything.
		assertThat(Files.isRegularFile(skill.script(skill.scriptFileName()).getScriptPath())).isTrue();
		assertThat(List.of(skill.scriptFileName())).isEqualTo(skill.scriptFiles());
	}

}
