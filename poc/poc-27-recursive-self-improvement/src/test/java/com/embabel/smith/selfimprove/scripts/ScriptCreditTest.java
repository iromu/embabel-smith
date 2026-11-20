/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.scripts;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.embabel.smith.builder.skill.Skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 6.2, offline: a generated script runs through POC 26's engine inside the
 * candidate's own skill folder, a run that completes credits the skill, and a run that
 * ends in an error withholds credit and says which skill it is holding.
 *
 * <p>
 * The three ways a script can fail are covered because POC 26's engine reports them
 * differently, and the one that is easy to miss is the loudest: a script that calls
 * {@code process.exit(2)} comes back as a {@code Success} carrying exit code 2, because
 * the engine treats the exit code the way a shell treats one. A gate that asked only "did
 * the engine return a failure" would credit a script that announced its own failure, so
 * that case is tested on its own rather than folded in with the thrown one.
 *
 * <p>
 * The paired-input test is the control that makes credit an earned result: the same
 * script, run twice, credited on one input and withheld on the other. Without it,
 * "credited" would be indistinguishable from "the folder was there".
 *
 * @author Ivan Rodriguez
 */
class ScriptCreditTest {

	private static final String STDIN = "draft: a compiled spec that names every stage";

	@TempDir
	Path workDir;

	private final SkillCredit credit = new SkillCredit(ScriptFixtures.ENGINE);

	@Test
	void aScriptThatRunsCreditsTheSkill() {
		StepSkillGenerator.Generated skill = ScriptFixtures.generated(this.workDir, """
				const draft = stdin.trim();
				console.log('scored ' + draft.length + ' characters of draft');
				""");

		SkillCredit.Ledger ledger = this.credit.run(skill, STDIN);

		assertThat(ledger.outcomes()).hasSize(1);
		assertThat(ledger.outcomes().get(0).credited()).isTrue();
		assertThat(ledger.creditedSkills()).containsExactly(skill.skillName());
		assertThat(ledger.withheld()).isEmpty();
	}

	@Test
	void aScriptThatExitsWithAnErrorWithholdsCreditAndNamesTheSkill() {
		StepSkillGenerator.Generated skill = ScriptFixtures.generated(this.workDir, """
				console.error('no draft to score');
				process.exit(2);
				""");

		SkillCredit.Ledger ledger = this.credit.run(skill, STDIN);

		assertThat(ledger.creditedSkills()).as("a script that announced its own failure")
			.doesNotContain(skill.skillName());
		assertThat(ledger.withheld()).hasSize(1);
		SkillCredit.Outcome outcome = ledger.withheld().get(0);
		assertThat(outcome.credited()).isFalse();
		// The attribution carries the skill, because the failure is a fact about the
		// candidate that wrote the script and has to be reportable against it.
		assertThat(outcome.attribution()).startsWith(skill.skillName()).contains(skill.scriptFileName());
		assertThat(outcome.diagnostic()).contains("exited with code 2").contains("no draft to score");
	}

	@Test
	void aScriptThatThrowsIsAttributedToTheSkillThatWroteIt() {
		StepSkillGenerator.Generated skill = ScriptFixtures.generated(this.workDir, """
				console.log('about to try');
				throw new TypeError('draft is not machine-readable');
				""");

		SkillCredit.Ledger ledger = this.credit.run(skill, STDIN);

		assertThat(ledger.credited(skill.skillName())).isFalse();
		assertThat(ledger.withheld()).hasSize(1);
		// The engine's own diagnostic survives, so the skill's failure is recorded as the
		// interpreter described it rather than as a generic "did not work".
		assertThat(ledger.withheld().get(0).diagnostic()).contains("draft is not machine-readable");
		assertThat(ledger.withheld().get(0).attribution()).startsWith(skill.skillName());
	}

	@Test
	void creditFollowsWhatTheScriptDidWithTheInputItWasGiven() {
		StepSkillGenerator.Generated skill = ScriptFixtures.generated(this.workDir, """
				const draft = stdin.trim();
				if (!draft) {
				  console.error('empty stdin');
				  process.exit(3);
				}
				console.log('scored ' + draft.length);
				""");

		// Same script, same folder, two inputs: credit tracks the run rather than the
		// presence of a scripts/ folder, and the script really did see what it was
		// handed.
		assertThat(this.credit.run(skill, STDIN).credited(skill.skillName())).isTrue();
		SkillCredit.Ledger withheld = this.credit.run(skill, "");
		assertThat(withheld.credited(skill.skillName())).isFalse();
		assertThat(withheld.withheld().get(0).diagnostic()).contains("exited with code 3");
	}

	@Test
	void aSkillWithNoScriptRunsNothingAndEarnsNothing() {
		String name = "evaluate-draft-score-draft";
		Path skillDir = this.workDir.resolve(name);
		try {
			Files.createDirectories(skillDir.resolve("scripts"));
		}
		catch (IOException ex) {
			throw new UncheckedIOException("could not create the empty skill folder", ex);
		}
		StepSkillGenerator.Generated nothing = new StepSkillGenerator.Generated(name,
				new Skill(name, "Scores a draft against the frozen tasks.", "Body"), skillDir, "score-draft.js");

		SkillCredit.Ledger ledger = this.credit.run(nothing, STDIN);

		// Credit has to be earned by a run. A folder that carries no script ran nothing,
		// so the ledger's silence is the withheld verdict rather than an absent one.
		assertThat(nothing.scriptFiles()).isEmpty();
		assertThat(ledger.outcomes()).isEmpty();
		assertThat(ledger.creditedSkills()).isEmpty();
		assertThat(ledger.credited(name)).isFalse();
	}

}
