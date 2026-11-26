/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.process;

import java.util.concurrent.atomic.AtomicReference;

import com.embabel.smith.builder.core.AgentsPackDraft;
import com.embabel.smith.builder.core.SingleShotAgentsPackGenerator;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentsPack;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Task 5.1, offline half: the self-description is what the builder's own single-shot
 * process generator is handed, and two agent specs come out the other end.
 *
 * <p>
 * Offline — the model answer is canned through the generator's own
 * {@code SpecPromptCaller} seam, the same way the builder's
 * {@code SingleShotAgentsPackGeneratorTest} tests it, so a failure here is about the
 * wiring between the self-description and the generator and not about what a model chose
 * to emit. The prompt is {@link SingleShotAgentsPackGenerator#defaultPrompt()}, the
 * bundled {@code process_to_flow_multi} prompt, because the agents-pack candidate is
 * generated single-shot whatever technique the single-agent candidate used
 * ({@code docs/08-builder.md}).
 *
 * @author Ivan Rodriguez
 */
class SelfProcessGenerationOfflineTest {

	private final String selfDescription = SelfDescription.text();

	@Test
	void theWholeSelfDescriptionIsWhatTheGeneratorIsAskedToDecompose() {
		AtomicReference<String> system = new AtomicReference<>();
		AtomicReference<String> user = new AtomicReference<>();
		SingleShotAgentsPackGenerator generator = new SingleShotAgentsPackGenerator((sys, usr) -> {
			system.set(sys);
			user.set(usr);
			return ProcessFixtures.TWO_AGENT_ANSWER;
		}, SingleShotAgentsPackGenerator.defaultPrompt());

		generator.generateAgentsPack(this.selfDescription);

		// The bundled multi-agent prompt is the system message, so this is the
		// single-shot
		// technique and not a prompt written for this POC.
		assertThat(system.get()).isEqualTo(SingleShotAgentsPackGenerator.defaultPrompt());
		// Delivered whole, inside the document block the generator wraps its input in: a
		// truncated or summarised seed would silently change what the eight stages are
		// being
		// generated from.
		assertThat(user.get()).contains("<document>\n" + this.selfDescription + "\n</document>");
		assertThat(user.get()).contains("## Pipeline stages");
	}

	@Test
	void theSelfDescriptionDecomposesIntoTwoAgentSpecsThatHandOff() {
		AgentsPackDraft draft = generator(ProcessFixtures.TWO_AGENT_ANSWER).generateAgentsPack(this.selfDescription);

		AgentsPack process = draft.process();
		assertThat(process.agents()).as(() -> draft.processName()).hasSizeGreaterThanOrEqualTo(2);
		assertThat(process.process()).isEqualTo("self-build");
		assertThat(draft.issues()).isEmpty();
		process.agents()
			.forEach(
					(agent) -> assertThat(agent.steps()).as("agent '" + agent.name() + "' has no step to hand off from")
						.isNotEmpty());
		assertThat(process.finalOutputType()).as("the process declares a final deliverable").isNotBlank();

		// The hand-off is a shared type, not two packs that happen to be listed together.
		assertThat(process.agents()).extracting(AgentSpec::name).containsExactly("compile-description", "publish-pack");
	}

	@Test
	void anAnswerThatYieldsOneAgentIsRefusedRatherThanReportedAsAProcess() {
		// The boundary task 5.1 asserts is enforced rather than decorative: one pack is
		// the
		// single-agent candidate, and accepting it here would let a build report a lone
		// pack
		// as a multi-agent process.
		String oneAgent = """
				{
				  "process": "solo",
				  "agents": [
				    {
				      "manifest": { "name": "build-everything", "version": "1.0.0" },
				      "goal": { "stepType": "goal", "name": "pack_published", "description": "desc",
				                "outputTypeName": "PublishedPack", "export": true },
				      "steps": [
				        { "stepType": "action", "name": "build", "description": "desc",
				         "inputTypeNames": ["UserInput"], "outputTypeName": "PublishedPack",
				         "prompt": "Build {{ userInput }}" }
				      ],
				      "types": []
				    }
				  ]
				}
				""";

		assertThatThrownBy(() -> generator(oneAgent).generateAgentsPack(this.selfDescription))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("single agent");
	}

	private static SingleShotAgentsPackGenerator generator(String answer) {
		return new SingleShotAgentsPackGenerator((system, user) -> answer,
				SingleShotAgentsPackGenerator.defaultPrompt());
	}

}
