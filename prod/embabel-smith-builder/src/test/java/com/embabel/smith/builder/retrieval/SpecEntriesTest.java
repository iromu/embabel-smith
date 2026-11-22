/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.retrieval;

import java.util.List;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.common.model.GoalStepSpec;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The matched text is the pack's meaning, not only its types: a task worded purely from a
 * step description has to reach the pack, and a pack that carries no descriptions at all
 * still has to be rankable on the types it names.
 */
class SpecEntriesTest {

	private final LexicalScorer scorer = new LexicalScorer();

	@Test
	void aQueryWordedOnlyFromAStepDescriptionMatchesThePack() {
		SpecEntry summarise = SpecEntries
			.of(pack("summarise", null, "condense a quarterly revenue filing into bullet points"));
		SpecEntry deploy = SpecEntries.of(pack("deploy", null, "roll the container out to the cluster"));

		assertThat(this.scorer.score("quarterly revenue filing", summarise)).isGreaterThan(0.0);
		assertThat(this.scorer.score("quarterly revenue filing", deploy)).isZero();
	}

	@Test
	void theGoalDescriptionIsPartOfTheMatchedText() {
		// None of these words appears in the pack's name or its type names, so the match
		// can only come from the goal description.
		SpecEntry entry = SpecEntries.of(pack("brief", "produce a condensed onboarding digest", null));

		assertThat(this.scorer.score("condensed onboarding digest", entry)).isGreaterThan(0.0);
	}

	@Test
	void aPackWithBlankDescriptionsStillCarriesItsTypeNames() {
		SpecEntry entry = SpecEntries.of(pack("nameless", null, null));

		assertThat(entry.inputTypes()).containsExactly("UserInput");
		assertThat(entry.outputType()).isEqualTo("Report");
		assertThat(entry.description()).contains("UserInput").contains("Report");
	}

	@Test
	void anInternalTypeIsNotAnExternalInput() {
		// The second step consumes what the first produces, so only the entry type is
		// needed from outside the pack.
		AgentSpec twoSteps = new AgentSpec(new AgentSpecManifest("two-step", "1.0.0"),
				GoalStepSpec.exportedGoal("two-step", "goal", "Report"),
				List.of(ActionStepSpec.action("first", "first step", List.of("UserInput"), "Draft", "p"),
						ActionStepSpec.action("second", "second step", List.of("Draft"), "Report", "p")),
				List.of());

		assertThat(SpecEntries.of(twoSteps).inputTypes()).containsExactly("UserInput");
	}

	private static AgentSpec pack(String name, String goalDescription, String stepDescription) {
		return new AgentSpec(new AgentSpecManifest(name, "1.0.0"),
				GoalStepSpec.exportedGoal(name, goalDescription, "Report"),
				List.of(ActionStepSpec.action("step", stepDescription, List.of("UserInput"), "Report", "do it")),
				List.of());
	}

}
