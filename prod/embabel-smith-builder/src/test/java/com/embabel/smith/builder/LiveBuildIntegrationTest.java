package com.embabel.smith.builder;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.smith.builder.core.AgentSpecGenerators;
import com.embabel.smith.builder.core.BuilderAgent;
import com.embabel.smith.builder.core.GenerationTechnique;
import com.embabel.smith.builder.eval.AgentSpecInvokerFactory;
import com.embabel.smith.builder.eval.EvalTask;
import com.embabel.smith.builder.eval.LlmPromptRewriter;
import com.embabel.smith.builder.publish.PublishTarget;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Endpoint-gated build integration test (task 5.2): builds a spec from a natural-language
 * description with the real model, runs it through the full pipeline (eval → refine →
 * security → lifecycle → publish), and asserts the pack lands on disk with its sidecar.
 * Skipped when the model endpoint is not configured.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OPENAI_CUSTOM_BASE_URL", matches = ".+")
class LiveBuildIntegrationTest {

	@Autowired
	Ai ai;

	@Autowired
	AgentPlatform platform;

	@ParameterizedTest
	@EnumSource(GenerationTechnique.class)
	void buildsAndPublishesAnAgentSpecWithEachTechnique(GenerationTechnique technique, @TempDir Path library)
			throws Exception {
		Path scratch = Files.createTempDirectory("smith-live-scratch-");
		BuilderAgent agent = new BuilderAgent(AgentSpecGenerators.production(this.ai, this.platform),
				scratch.toString());
		BuilderService service = new BuilderService(agent, new AgentSpecInvokerFactory(this.platform),
				new LlmPromptRewriter(env("OPENAI_CUSTOM_BASE_URL"), env("OPENAI_CUSTOM_API_KEY"), firstModel()),
				List.of(new EvalTask("runs-and-produces-output", "Describe your use case.", "")));

		BuildResult result = service.build("explain how a coffee machine works",
				PublishTarget.parse("folder:" + library),
				new BuildOptions(BuildOptions.DEFAULT_MAX_REFINE, BuildOptions.DEFAULT_SCORE_GATE, false, false,
						technique));

		System.out.print(result.report());
		assertThat(result.isBuilt()).as(() -> result.report()).isTrue();
		Path packDir = result.published().location();
		assertThat(packDir.resolve("manifest.yml")).isRegularFile();
		assertThat(packDir.resolve("lifecycle.yml")).isRegularFile();
	}

	private static String env(String name) {
		String value = System.getenv(name);
		return value == null ? "" : value;
	}

	private static String firstModel() {
		String models = env("OPENAI_CUSTOM_MODELS");
		return models.isBlank() ? "" : models.split(",")[0].trim();
	}

}
