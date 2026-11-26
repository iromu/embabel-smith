/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.eval;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.embabel.smith.builder.core.BuilderPackPaths;
import com.embabel.smith.builder.core.CompiledDraft;
import com.embabel.smith.common.io.AgentSpecReader;
import com.embabel.smith.common.model.AgentSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What the pack flow does between a description and a draft, offline: it reads the
 * builder pack from this module's {@code specs/} resources, deploys it through
 * {@link InvokerFactory} — the seam {@link AgentSpecInvokerFactory} implements, so
 * nothing here needs a platform — and runs its exported goal. The invoker answers with
 * the same JSON the single-shot generator would have parsed, so the assertions are about
 * the route rather than about a model.
 *
 * <p>
 * The failure half is task 3.4: each way this generator can fail names the flow that
 * failed, and none of them reaches the Java generator, because a pack flow wires the Java
 * generator out of the build (see {@code cli/BuildCommandTest}'s generator-map case).
 *
 * @author Ivan Rodriguez
 */
class PackDrivenAgentSpecGeneratorTest {

	/**
	 * The pack {@code cli/BuildCommand} drives under {@code --flow pack}, addressed
	 * through the class loader as {@link PackDrivenAgentSpecGenerator#builderPack()}
	 * addresses it.
	 */
	private static final Path PACK_DIR = BuilderPackPaths.directory("builder-spec-one-shot");

	/** The goal {@code builder-spec-one-shot/goal.yml} exports. */
	private static final String GOAL = "agentSpecDraftedInOneCall";

	/** The answer a step would give: an agent spec as JSON text. */
	private static final String SPEC_JSON = """
			{
			  "manifest": { "name": "drafted-pack", "version": "1.0.0" },
			  "goal": {
			    "stepType": "goal", "name": "answered", "outputTypeName": "Answer",
			    "description": "The answer was produced", "export": true
			  },
			  "steps": [
			    {
			      "stepType": "action", "name": "answer", "inputTypeNames": ["UserInput"],
			      "outputTypeName": "Answer", "description": "Answers",
			      "prompt": "Answer this: {{userInput.content}}", "toolGroups": []
			    }
			  ],
			  "types": [
			    { "name": "Answer", "description": "The answer", "ownProperties": [
			      { "name": "text", "type": "string", "description": "The text" }], "parents": [] }
			  ]
			}""";

	/**
	 * Records the pack each deploy was handed and the text each invocation sent, and
	 * answers with whatever this test decided the model would say.
	 */
	private static final class RecordingInvokers implements InvokerFactory {

		private final List<String> packs = new ArrayList<>();

		private final List<String> inputs = new ArrayList<>();

		private final AtomicInteger calls = new AtomicInteger();

		private final String answer;

		private String seenModel;

		RecordingInvokers(String answer) {
			this.answer = answer;
		}

		@Override
		public AgentSpecInvoker invokerFor(AgentSpec pack) {
			return invokerFor(pack, null);
		}

		@Override
		public AgentSpecInvoker invokerFor(AgentSpec pack, String model) {
			this.packs.add(pack.name());
			this.seenModel = model;
			return (input) -> {
				this.calls.incrementAndGet();
				this.inputs.add(input);
				return this.answer;
			};
		}

	}

	@Test
	void thePackFlowDeploysThePackFromDiskAndRunsItsExportedGoalOnTheDescriptionAsGiven() {
		RecordingInvokers invokers = new RecordingInvokers(SPEC_JSON);

		CompiledDraft draft = new PackDrivenAgentSpecGenerator(invokers, PACK_DIR).generate("Ship orders end to end");

		assertThat(invokers.packs).as("the pack it deployed").containsExactly("builder-spec-one-shot");
		assertThat(invokers.inputs).as("one call, the shipped pack's goal").containsExactly("Ship orders end to end");
		assertThat(draft.agentName()).isEqualTo("drafted-pack");
		assertThat(draft.pack().name()).isEqualTo("drafted-pack");
		assertThat(draft.issues()).isEmpty();
	}

	@Test
	void theRequestedModelIsPassedThroughToTheDeployedSteps() {
		RecordingInvokers invokers = new RecordingInvokers(SPEC_JSON);

		new PackDrivenAgentSpecGenerator(invokers, PACK_DIR).generate("a description", "chosen-model");

		assertThat(invokers.seenModel).as("the model the build asked for reaches the deploy").isEqualTo("chosen-model");
	}

	@Test
	void aPackThatWillNotLoadNamesTheFlowAndNeverAsksForADeploy(@TempDir Path missing) {
		RecordingInvokers invokers = new RecordingInvokers(SPEC_JSON);
		Path absent = missing.resolve("builder-spec-one-shot");

		assertThatThrownBy(() -> new PackDrivenAgentSpecGenerator(invokers, absent).generate("a description"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("pack flow")
			.hasMessageContaining("builder-spec-one-shot");

		assertThat(invokers.packs).as("a pack that will not load is never deployed").isEmpty();
		assertThat(invokers.calls).as("and no call was spent on it").hasValue(0);
	}

	@Test
	void aGoalThatProducedNothingFailsNamingTheFlowAndTheGoal() {
		RecordingInvokers invokers = new RecordingInvokers(null);

		assertThatThrownBy(() -> new PackDrivenAgentSpecGenerator(invokers, PACK_DIR).generate("a description"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("pack flow")
			.hasMessageContaining(GOAL);
	}

	@Test
	void anAnswerThatCarriesNoSpecJsonFailsNamingTheFlowAndShowsWhatCameBack() {
		RecordingInvokers invokers = new RecordingInvokers("I cannot help with that.");

		assertThatThrownBy(() -> new PackDrivenAgentSpecGenerator(invokers, PACK_DIR).generate("a description"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("pack flow")
			.hasMessageContaining("I cannot help with that.");
	}

	/**
	 * The pack this flow drives is the shipped one, addressed through the class loader
	 * and readable by the same reader the eval uses, so the generator's failure message
	 * above is about a pack that exists rather than about a fixture this test forgot to
	 * write.
	 * @throws IOException when the pack cannot be read
	 */
	@Test
	void thePackItDrivesIsTheOneOnDisk() throws IOException {
		AgentSpec pack = new AgentSpecReader().read(PACK_DIR);

		assertThat(pack.name()).isEqualTo("builder-spec-one-shot");
		assertThat(pack.goal().name()).isEqualTo(GOAL);
	}

}
