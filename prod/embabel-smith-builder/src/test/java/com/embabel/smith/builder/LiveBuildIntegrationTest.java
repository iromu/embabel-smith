/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.smith.builder.core.AgentSpecGenerators;
import com.embabel.smith.builder.core.BuilderAgent;
import com.embabel.smith.builder.core.CompiledDraft;
import com.embabel.smith.builder.core.GenerationTechnique;
import com.embabel.smith.builder.core.SingleShotProcessSpecGenerator;
import com.embabel.smith.builder.eval.AgentSpecInvokerFactory;
import com.embabel.smith.builder.eval.AiPromptRewriter;
import com.embabel.smith.builder.eval.EvalTask;
import com.embabel.smith.builder.publish.PublishTarget;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Endpoint-gated build integration test: exercises the real model against the pipeline
 * (generate both candidates → validate → write → score both → recommend → security →
 * lifecycle → publish). Skipped when the model endpoint is not configured.
 *
 * <p>
 * The two techniques are asserted at different depths, because they are not equally
 * strong (see the builder README's generation-techniques table). The single-shot
 * technique — the build default — is asserted all the way to a published pack. The
 * embabel technique is asserted at the generation seam, which is what its spec
 * requirement claims.
 */
@SpringBootTest
@Tag("live")
@EnabledIfEnvironmentVariable(named = "OPENAI_CUSTOM_BASE_URL", matches = ".+")
class LiveBuildIntegrationTest {

	private static final String DESCRIPTION = "explain how a coffee machine works";

	@Autowired
	Ai ai;

	@Autowired
	AgentPlatform platform;

	@Test
	void singleShotBuildPublishesAValidPack(@TempDir Path library) throws Exception {
		BuildResult result = service(library).build(DESCRIPTION, PublishTarget.parse("folder:" + library),
				BuildOptions.defaults());

		System.out.print(result.report());
		assertThat(result.isBuilt()).as(() -> result.report()).isTrue();
		assertThat(result.recommendation()).as("the build weighed both candidates").isNotNull();
		assertThat(result.recommendation().single().score()).as("the pack was scored").isNotNull();
		Path published = Path.of(result.published().location());
		// Whatever the build recommended is what landed: a pack, or a process of packs.
		assertThat(Files.isRegularFile(published.resolve("manifest.yml"))
				|| Files.isRegularFile(published.resolve("process.yml")))
			.isTrue();
		assertThat(published.resolve("lifecycle.yml")).isRegularFile();
	}

	/**
	 * Asserts the embabel technique does what its requirement claims — the description is
	 * compiled by Embabel's NL compiler into the shared agent-spec format — without
	 * asserting a published pack. The compiler returns its actions with no intermediate
	 * types, so step and goal output types reach the validator undeclared
	 * ({@code DANGLING_TYPE_REF}) and the build blocks before publishing; POC 24 measured
	 * this technique at 0/6 valid. Synthesizing the missing types would publish a pack
	 * whose {@code types/} carry names but no properties, so the technique's output is
	 * checked at the seam rather than papered over downstream.
	 */
	@Test
	void embabelTechniqueCompilesTheDescriptionToTheSharedFormat() {
		CompiledDraft draft = AgentSpecGenerators.production(this.ai, this.platform)
			.get(GenerationTechnique.EMBABEL)
			.generate(DESCRIPTION);

		assertThat(draft.agentName()).isNotBlank();
		assertThat(draft.pack().steps()).isNotEmpty();
		assertThat(draft.pack().goal()).isNotNull();
		assertThat(draft.pack().goal().isExported()).isTrue();
		draft.pack().steps().forEach((step) -> {
			assertThat(step.name()).isNotBlank();
			assertThat(step.outputTypeName()).isNotBlank();
		});
	}

	private BuilderService service(Path library) throws Exception {
		Path scratch = Files.createTempDirectory("smith-live-scratch-");
		BuilderAgent agent = new BuilderAgent(AgentSpecGenerators.production(this.ai, this.platform),
				scratch.toString());
		// The wiring the CLI builds with, so the live run exercises the dual-candidate
		// pipeline rather than a configuration no product path uses.
		AgentSpecInvokerFactory invokers = new AgentSpecInvokerFactory(this.platform);
		return new BuilderService(agent, invokers, invokers, new SingleShotProcessSpecGenerator(this.ai),
				new AiPromptRewriter(this.ai),
				List.of(new EvalTask("runs-and-produces-output", "Describe your use case.", "")));
	}

}
