/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.common.io;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.samples.SampleAgentSpecs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies task 1.2: a agent spec written to disk can be read back losslessly.
 */
class AgentSpecRoundTripTest {

	@Test
	void writesThenReadsBackLosslessly(@TempDir Path dir) throws Exception {
		AgentSpec pack = SampleAgentSpecs.explainer();

		Path written = new AgentSpecWriter().write(pack, dir);
		AgentSpec readBack = new AgentSpecReader().read(written);

		assertThat(readBack).isEqualTo(pack);
		assertThat(readBack.name()).isEqualTo("explainer");
		assertThat(readBack.goalOutputType()).isEqualTo(SampleAgentSpecs.BACKGROUND_MESSAGE);
		assertThat(readBack.steps()).hasSize(2);
		assertThat(readBack.types()).hasSize(1);
	}

	@Test
	void producesTheExpectedDirectoryLayout(@TempDir Path dir) throws Exception {
		new AgentSpecWriter().write(SampleAgentSpecs.explainer(), dir);

		assertThat(Files.isRegularFile(dir.resolve("manifest.yml"))).isTrue();
		assertThat(Files.isRegularFile(dir.resolve("goal.yml"))).isTrue();
		assertThat(Files.isRegularFile(dir.resolve("steps/extractTopic.yml"))).isTrue();
		assertThat(Files.isRegularFile(dir.resolve("steps/writeExplainer.yml"))).isTrue();
		assertThat(Files.isRegularFile(dir.resolve("types/TopicBrief.yml"))).isTrue();
	}

	@Test
	void roundTripsAPackWithNoIntermediateTypes(@TempDir Path dir) throws Exception {
		AgentSpec pack = SampleAgentSpecs.singleStep();

		new AgentSpecWriter().write(pack, dir);
		AgentSpec readBack = new AgentSpecReader().read(dir);

		assertThat(readBack).isEqualTo(pack);
		assertThat(readBack.types()).isEmpty();
		// the empty types/ dir is still present (with a .gitkeep placeholder)
		assertThat(Files.isDirectory(dir.resolve("types"))).isTrue();
	}

	@Test
	void rewritesIdempotentlyClearingStaleFiles(@TempDir Path dir) throws Exception {
		AgentSpecWriter writer = new AgentSpecWriter();

		// First write a pack that has two steps and an intermediate type.
		writer.write(SampleAgentSpecs.explainer(), dir);
		assertThat(Files.isRegularFile(dir.resolve("types/TopicBrief.yml"))).isTrue();
		assertThat(Files.isRegularFile(dir.resolve("steps/extractTopic.yml"))).isTrue();

		// Re-write the SAME directory with a pack that has one step and no types.
		writer.write(SampleAgentSpecs.singleStep(), dir);

		AgentSpec readBack = new AgentSpecReader().read(dir);
		assertThat(readBack).isEqualTo(SampleAgentSpecs.singleStep());
		assertThat(readBack.types()).isEmpty();
		// Stale files from the first write are gone; the empty types/ now has a .gitkeep.
		assertThat(Files.isRegularFile(dir.resolve("types/TopicBrief.yml"))).isFalse();
		assertThat(Files.isRegularFile(dir.resolve("steps/extractTopic.yml"))).isFalse();
		assertThat(Files.isRegularFile(dir.resolve("steps/say.yml"))).isTrue();
		assertThat(Files.isRegularFile(dir.resolve("types/.gitkeep"))).isTrue();
	}

	@Test
	void roundTripsAStepThatDeclaresReferences(@TempDir Path dir) throws Exception {
		AgentSpec pack = packDeclaringReferences("repo-notes", "shell-helpers");

		new AgentSpecWriter().write(pack, dir);
		AgentSpec readBack = new AgentSpecReader().read(dir);

		assertThat(readBack).isEqualTo(pack);
		assertThat(readBack.step("noteItDown").references()).containsExactly("repo-notes", "shell-helpers");

		// Rewrite from what was READ, and check the step file rather than only the model:
		// the reader drops an unknown key silently, so a rewrite that stops serializing
		// the
		// component loses the capability without failing anything until the second write.
		Path rewritten = dir.resolve("rewritten");
		new AgentSpecWriter().write(readBack, rewritten);

		String stepFile = Files.readString(rewritten.resolve("steps/noteItDown.yml"));
		assertThat(stepFile).contains("references:").contains("repo-notes").contains("shell-helpers");
		assertThat(new AgentSpecReader().read(rewritten).step("noteItDown").references()).containsExactly("repo-notes",
				"shell-helpers");
	}

	@Test
	void writesNoReferencesForAStepThatDeclaresNone(@TempDir Path dir) throws Exception {
		new AgentSpecWriter().write(SampleAgentSpecs.explainer(), dir);

		assertThat(new AgentSpecReader().read(dir).steps()).allMatch((step) -> step.references().isEmpty());
	}

	private static AgentSpec packDeclaringReferences(String... references) {
		GoalStepSpec goal = new GoalStepSpec("goal", "notesTaken", "Notes have been taken",
				SampleAgentSpecs.BACKGROUND_MESSAGE, true);
		ActionStepSpec step = new ActionStepSpec(ActionStepSpec.STEP_TYPE, "noteItDown", "Take notes down",
				List.of(SampleAgentSpecs.USER_INPUT), SampleAgentSpecs.BACKGROUND_MESSAGE,
				"Take notes on: {{userInput.content}}", null, null, null, null, null, null, null, List.of(references));
		return new AgentSpec(new AgentSpecManifest("notes", "1.0.0"), goal, List.of(step), List.of());
	}

}
