/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.a2a;

import java.nio.file.Path;
import java.util.Set;

import com.embabel.agent.core.Goal;
import com.embabel.agent.spec.model.BackgroundMessage;
import com.embabel.smith.runtime.core.TrustTier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The card's goal filter on its own. Which goals a tier can run is decided by the packs
 * in the configured source — tool groups are declared on steps, so a goal inherits its
 * tier requirement from the pack that exports it. The {@code shell} pack declares the
 * {@code shell} tool group, which only the high tier allows.
 */
class TrustTierGoalFilterTest {

	private static final String EXPLAIN_GOAL = "explainDone";

	private static final String SHELL_GOAL = "cliDone";

	@Test
	void theStandardTierRefusesTheGoalOfThePackThatDeclaresShellAndKeepsTheOther(@TempDir Path tmp) {
		String source = "folder:" + A2aTestPacks.library(tmp, A2aTestPacks.EXPLAIN, A2aTestPacks.SHELL);

		TrustTierGoalFilter filter = new TrustTierGoalFilter(source, TrustTier.STANDARD);

		assertThat(filter.allows(goal(EXPLAIN_GOAL))).isTrue();
		assertThat(filter.allows(goal(SHELL_GOAL))).isFalse();
	}

	@Test
	void theHighTierAdvertisesTheSameTwoGoalsFromTheSameSource(@TempDir Path tmp) {
		// The control on the test above: the exclusion is the tier's and not a name the
		// filter knows about, so with the higher tier both goals are admissible.
		String source = "folder:" + A2aTestPacks.library(tmp, A2aTestPacks.EXPLAIN, A2aTestPacks.SHELL);

		TrustTierGoalFilter filter = new TrustTierGoalFilter(source, TrustTier.HIGH);

		assertThat(filter.allows(goal(EXPLAIN_GOAL))).isTrue();
		assertThat(filter.allows(goal(SHELL_GOAL))).isTrue();
	}

	@Test
	void aSourceThatCannotBeReadAdmitsEveryGoalBecauseTheRunStillGates(@TempDir Path tmp) {
		TrustTierGoalFilter filter = new TrustTierGoalFilter("folder:" + tmp.resolve("absent"), TrustTier.STANDARD);

		assertThat(filter.allows(goal(SHELL_GOAL))).isTrue();
	}

	@Test
	void anEndpointWithNoSourceConfiguredAdmitsEveryGoal() {
		assertThat(new TrustTierGoalFilter("", TrustTier.STANDARD).allows(goal(SHELL_GOAL))).isTrue();
	}

	private static Goal goal(String name) {
		// createInstance takes the description first and the name third, and its
		// three-argument form derives the name from the type — so the five-argument form
		// is the one that actually produces a goal called `name`.
		return Goal.createInstance("the " + name + " goal", BackgroundMessage.class, name, Set.of(), Set.of());
	}

}
