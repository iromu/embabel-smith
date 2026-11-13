package com.embabel.smith.studio.rest;

import com.embabel.smith.common.config.ModelEndpointConfig;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.studio.generate.BlueprintLoader;
import com.embabel.smith.studio.rest.dto.GenerateRequest;
import com.embabel.smith.studio.rest.dto.GenerateResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline tests for the Author-a-Process generation step: the endpoint gate, the no-blueprint
 * guard, and delegation to the seam (including the generation technique).
 */
class GenerateControllerTest {

	private final BlueprintLoader loader = new BlueprintLoader();

	@Test
	void returnsStructuredErrorWhenNoEndpoint() {
		GenerateController controller = new GenerateController((blueprint, technique) -> {
			throw new AssertionError("must not generate without an endpoint");
		}, new ModelEndpointConfig("", "", "", ""), loader);
		GenerateResponse response = controller.generate(new GenerateRequest("a business process", null, "single-shot"));
		assertThat(response.errorCode()).isEqualTo("MODEL_NOT_CONFIGURED");
		assertThat(response.error()).contains("No resolvable model endpoint");
		assertThat(response.pack()).isNull();
	}

	@Test
	void returnsErrorWhenNoBlueprint() {
		GenerateController controller = new GenerateController((blueprint, technique) -> {
			throw new AssertionError("must not generate without a blueprint");
		}, configured(), loader);
		GenerateResponse response = controller.generate(new GenerateRequest("", null, "single-shot"));
		assertThat(response.error()).contains("No blueprint");
	}

	@Test
	void passesTheRequestedTechniqueToTheSeam() {
		AtomicReference<String> seenTechnique = new AtomicReference<>();
		GenerateController controller = new GenerateController((blueprint, technique) -> {
			seenTechnique.set(technique);
			return GenerateResponse.of(pack(), List.of());
		}, configured(), loader);
		controller.generate(new GenerateRequest("a business process", null, "embabel"));
		assertThat(seenTechnique.get()).isEqualTo("embabel");
	}

	@Test
	void returnsTheGeneratedPackAndIssuesWhenConfigured() {
		GenerateController controller = new GenerateController((blueprint, technique) -> GenerateResponse.of(pack(),
				List.of("an issue")), configured(), loader);
		GenerateResponse response = controller.generate(new GenerateRequest("a business process", null, "single-shot"));
		assertThat(response.pack()).isNotNull();
		assertThat(response.pack().name()).isEqualTo("sample");
		assertThat(response.issues()).containsExactly("an issue");
		assertThat(response.error()).isNull();
	}

	private static AgentSpec pack() {
		return new AgentSpec(new AgentSpecManifest("sample", "1.0.0"),
				GoalStepSpec.exportedGoal("sample", "The goal", "Result"),
				List.of(ActionStepSpec.action("do", "Do the thing", List.of("UserInput"), "Result", "Do it")),
				List.of());
	}

	private static ModelEndpointConfig configured() {
		return new ModelEndpointConfig("http://localhost:4000", "key", "gpt", "gpt");
	}

}
