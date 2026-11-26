/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.process;

import java.util.stream.Stream;

import com.embabel.agent.api.common.Ai;
import com.embabel.smith.builder.core.AgentsPackDraft;
import com.embabel.smith.builder.core.SingleShotAgentsPackGenerator;
import com.embabel.smith.selfimprove.SelfImproveApp;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tasks 5.1 and 5.3, live half: the builder's own single-shot process generator
 * decomposes the self-description into a multi-agent process, and that process is then
 * measured against the pipeline the builder actually runs.
 *
 * <p>
 * <b>One model call, shared by both checks.</b> The generation happens once in
 * {@link #generateFromTheSelfDescription()} so the shape assertion and the stage-coverage
 * assertion describe the same answer; a second call would let them disagree for no reason
 * but model noise, and the paid budget is the POC's to account for.
 *
 * <p>
 * The assertions are about shape, deliberately. Nothing here names an agent, a step or a
 * type the model chose, because this repo's known live-test failure is a test that breaks
 * on model variance rather than on a code change (see {@code LiveExampleBuildTest}, which
 * asserts the same way for the same reason). Which stages the generation dropped is
 * <em>recorded</em>, not asserted: the self-description lists eight, and a generation
 * that lost two is the finding task 5.3 asks for, so pinning a coverage count here would
 * freeze one run's answer into permanent evidence.
 *
 * <p>
 * Spring is started by hand rather than with {@code @SpringBootTest}, because this
 * module's test classpath carries JUnit and AssertJ only — no
 * {@code spring-boot-starter-test} — and adding a dependency to the shared POM is not
 * this section's to do. The same application class the launcher boots is what starts
 * here, so the live run exercises the POC's real model layer.
 *
 * @author Ivan Rodriguez
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "OPENAI_CUSTOM_BASE_URL", matches = ".+")
class SelfProcessGenerationLiveTest {

	private static ConfigurableApplicationContext context;

	private static AgentsPackDraft draft;

	@BeforeAll
	static void generateAMultiAgentProcessFromTheSelfDescription() {
		context = new SpringApplicationBuilder(SelfImproveApp.class).web(WebApplicationType.NONE)
			.bannerMode(Banner.Mode.OFF)
			.run();
		// The product's own generator with the product's own bundled prompt: single-shot,
		// the
		// technique docs/08-builder.md records as the only multi-agent one worth running.
		Ai ai = context.getBean(Ai.class);
		draft = new SingleShotAgentsPackGenerator(ai).generateAgentsPack(SelfDescription.text());
		System.out.println("poc-27 5.1: generated process '" + draft.processName() + "' with "
				+ draft.process().agents().size() + " agent specs from " + SelfDescription.RESOURCE);
	}

	@AfterAll
	static void stopTheEndpoint() {
		if (context != null) {
			context.close();
		}
	}

	@Test
	void theSelfDescriptionGeneratesAtLeastTwoAgentSpecs() {
		assertThat(draft.process().agents()).as(() -> draft.processName()).hasSizeGreaterThanOrEqualTo(2);
		draft.process()
			.agents()
			.forEach((agent) -> assertThat(agent.steps())
				.as("agent '" + agent.name() + "' declares no step, so it hands nothing to anyone")
				.isNotEmpty());
		assertThat(draft.process().finalOutputType()).as("no final deliverable to hand to the runtime").isNotBlank();
		draft.process()
			.agents()
			.forEach((agent) -> assertThat(agent.name()).as("an unnamed pack cannot be a directory").isNotBlank());
	}

	@Test
	void theGeneratedProcessIsMeasuredAgainstThePipelineTheBuilderRuns() {
		BuilderPipelineStages.Coverage coverage = BuilderPipelineStages.coverage(draft.process());
		// The record task 5.3 asks for, in the surefire report beside the assertion.
		System.out
			.println("poc-27 5.3: stages named " + coverage.named() + "; generation dropped " + coverage.dropped());

		// A partition of the eight documented stages, so a stage can be neither lost nor
		// invented by the check itself, and at least one named: a process that names none
		// of
		// them does not describe this pipeline, whatever else it describes.
		assertThat(Stream.concat(coverage.named().stream(), coverage.dropped().stream()).toList())
			.containsExactlyInAnyOrderElementsOf(BuilderPipelineStages.ids());
		assertThat(coverage.named()).as(() -> "dropped " + coverage.dropped()).isNotEmpty();
	}

}
