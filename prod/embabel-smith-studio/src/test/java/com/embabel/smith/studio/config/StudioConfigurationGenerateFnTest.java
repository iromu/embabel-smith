package com.embabel.smith.studio.config;

import com.embabel.smith.builder.core.AgentSpecGenerator;
import com.embabel.smith.builder.core.CompiledDraft;
import com.embabel.smith.builder.core.GenerationTechnique;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.studio.rest.GenerateController;
import com.embabel.smith.studio.rest.dto.GenerateResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline tests for the workspace's generate step now that it delegates to the builder's
 * engine: the technique selects the builder generator, an unrecognized technique means the
 * single-shot default, and a generator failure is reported as a structured failure.
 */
class StudioConfigurationGenerateFnTest {

	@Test
	void theSingleShotTechniqueUsesTheBuilderSingleShotGenerator() {
		AtomicReference<String> used = new AtomicReference<>();
		GenerateController.GenerateFn fn = StudioConfiguration.generateFn(generators(used));

		GenerateResponse response = fn.generate("a business process", "single-shot");

		assertThat(used.get()).isEqualTo("single-shot");
		assertThat(response.pack().name()).isEqualTo("from-single-shot");
		assertThat(response.error()).isNull();
	}

	@Test
	void theEmbabelTechniqueUsesTheBuilderEmbabelGenerator() {
		AtomicReference<String> used = new AtomicReference<>();
		GenerateController.GenerateFn fn = StudioConfiguration.generateFn(generators(used));

		GenerateResponse response = fn.generate("a business process", "embabel");

		assertThat(used.get()).isEqualTo("embabel");
		assertThat(response.pack().name()).isEqualTo("from-embabel");
	}

	@Test
	void anUnsetOrUnknownTechniqueMeansTheSingleShotDefault() {
		AtomicReference<String> used = new AtomicReference<>();
		GenerateController.GenerateFn fn = StudioConfiguration.generateFn(generators(used));

		fn.generate("a business process", null);
		assertThat(used.get()).isEqualTo("single-shot");

		fn.generate("a business process", "something-else");
		assertThat(used.get()).isEqualTo("single-shot");
	}

	@Test
	void aGeneratorFailureIsReportedAsAFailure() {
		AgentSpecGenerator failing = (description) -> {
			throw new IllegalStateException("endpoint unreachable");
		};
		GenerateController.GenerateFn fn = StudioConfiguration
			.generateFn(Map.of(GenerationTechnique.SINGLE_SHOT, failing, GenerationTechnique.EMBABEL, failing));

		GenerateResponse response = fn.generate("a business process", "single-shot");

		assertThat(response.error()).contains("Generation failed").contains("endpoint unreachable");
		assertThat(response.pack()).isNull();
	}

	private static Map<GenerationTechnique, AgentSpecGenerator> generators(AtomicReference<String> used) {
		return Map.of(GenerationTechnique.SINGLE_SHOT, draftGenerator("single-shot", "from-single-shot", used),
				GenerationTechnique.EMBABEL, draftGenerator("embabel", "from-embabel", used));
	}

	private static AgentSpecGenerator draftGenerator(String technique, String name, AtomicReference<String> used) {
		return (description) -> {
			used.set(technique);
			AgentSpec pack = new AgentSpec(new AgentSpecManifest(name, "1.0.0"),
					GoalStepSpec.exportedGoal(name, "The goal", "BackgroundMessage"),
					List.of(ActionStepSpec.action("say", "Say it", List.of("UserInput"), "BackgroundMessage",
							"Repeat: {{userInput.content}}")),
					List.of());
			return new CompiledDraft(name, pack, List.of());
		};
	}

}
