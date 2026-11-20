/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.a2a;

import java.nio.file.Path;
import java.util.List;

import com.embabel.smith.runtime.core.TrustTier;
import io.a2a.spec.AgentSkill;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The card's skill list, read from the configured source rather than from the platform.
 * Which packs a tier can run is decided per pack — tool groups are declared on steps, so
 * a goal inherits its tier requirement from the pack that exports it — and the
 * {@code shell} pack declares the {@code shell} group, which only the high tier allows.
 */
class SpecSourceSkillsTest {

	private static final String EXPLAIN_GOAL = "explainDone";

	private static final String SHELL_GOAL = "cliDone";

	@Test
	void theStandardTierAdvertisesTheAllowedPackAndOmitsThePackThatDeclaresShell(@TempDir Path tmp) {
		List<AgentSkill> skills = skills(tmp, TrustTier.STANDARD);

		assertThat(names(skills)).containsExactly(EXPLAIN_GOAL);
	}

	@Test
	void theHighTierAdvertisesBothGoalsFromTheSameSource(@TempDir Path tmp) {
		// The control on the test above: the omission is the tier's and not a name this
		// class knows about, so with the higher tier both packs are advertised.
		List<AgentSkill> skills = skills(tmp, TrustTier.HIGH);

		assertThat(names(skills)).containsExactlyInAnyOrder(EXPLAIN_GOAL, SHELL_GOAL);
	}

	/**
	 * What a skill can honestly say about a no-code pack. {@code tags} and
	 * {@code examples} are empty because {@code goal.yml} declares neither — they belong
	 * to {@code @Agent} on a Java class — so the assertion here is that nothing was
	 * invented to fill them.
	 */
	@Test
	void aSkillIsThePackGoalItselfWithTheModesTheLibraryPasses(@TempDir Path tmp) {
		AgentSkill skill = skills(tmp, TrustTier.STANDARD).get(0);

		assertThat(skill.id()).isEqualTo(EXPLAIN_GOAL);
		assertThat(skill.name()).isEqualTo(EXPLAIN_GOAL);
		assertThat(skill.description()).isEqualTo("A complete explanation has been produced");
		assertThat(skill.tags()).isEmpty();
		assertThat(skill.examples()).isEmpty();
		assertThat(skill.inputModes()).containsExactly("application/json");
		assertThat(skill.outputModes()).containsExactly("application/json");
	}

	/**
	 * The failure half of the lazy read. An empty card would tell a client the agent can
	 * do nothing, which is false in the direction that hides a misconfiguration, so the
	 * read raises naming the source instead. And the failure must not be remembered: the
	 * second call below reads a source that became readable and answers.
	 */
	@Test
	void anUnreadableSourceRaisesNamingItAndALaterCallSucceedsOnceItIsReadable(@TempDir Path tmp) {
		Path missing = tmp.resolve("not-yet-staged");
		SpecSourceSkills skills = new SpecSourceSkills("folder:" + missing, TrustTier.STANDARD);

		assertThatThrownBy(skills::skills).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("folder:" + missing);

		A2aTestPacks.library(missing, A2aTestPacks.EXPLAIN);

		assertThat(names(skills.skills())).containsExactly(EXPLAIN_GOAL);
	}

	/**
	 * An endpoint with no source configured cannot describe itself either. The old filter
	 * admitted every deployed goal in this case because the platform's goal set was
	 * something to fall back on; with the source as the only source of skills there is
	 * nothing to fall back to.
	 */
	@Test
	void anEndpointWithNoSourceConfiguredRaisesRatherThanAdvertiseNothing() {
		assertThatThrownBy(new SpecSourceSkills("", TrustTier.STANDARD)::skills)
			.isInstanceOf(IllegalStateException.class)
			.hasCauseInstanceOf(IllegalArgumentException.class);
	}

	/**
	 * The read is once-only: the second call must not re-resolve the source, because
	 * {@code SpecSource.resolve()} re-clones a {@code git:} source on every call. Staging
	 * a second pack into the library between the two reads proves the answer came from
	 * the cache rather than from the directory.
	 */
	@Test
	void theSourceIsReadOnceAndCached(@TempDir Path tmp) {
		Path library = A2aTestPacks.library(tmp, A2aTestPacks.EXPLAIN);
		SpecSourceSkills skills = new SpecSourceSkills("folder:" + library, TrustTier.STANDARD);

		assertThat(names(skills.skills())).containsExactly(EXPLAIN_GOAL);

		// A re-read of the source would now find a second goal; the card must not change.
		A2aTestPacks.library(library, A2aTestPacks.EXTRACT);

		assertThat(names(skills.skills())).as("the cached list stands; the source is not re-read")
			.containsExactly(EXPLAIN_GOAL);
	}

	private static List<AgentSkill> skills(Path tmp, TrustTier tier) {
		Path library = A2aTestPacks.library(tmp, A2aTestPacks.EXPLAIN, A2aTestPacks.SHELL);
		return new SpecSourceSkills("folder:" + library, tier).skills();
	}

	private static List<String> names(List<AgentSkill> skills) {
		return skills.stream().map(AgentSkill::name).toList();
	}

}
