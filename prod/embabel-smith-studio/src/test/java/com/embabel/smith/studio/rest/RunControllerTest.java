package com.embabel.smith.studio.rest;

import com.embabel.smith.common.config.ModelEndpointConfig;
import com.embabel.smith.runtime.AgentSpecRunService;
import com.embabel.smith.runtime.core.TrustTier;
import com.embabel.smith.runtime.trace.TraceRecord;
import com.embabel.smith.studio.rest.dto.RunRequest;
import com.embabel.smith.studio.rest.dto.RunResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline tests for the Run pane: the endpoint gate, the source guard, and delegation to
 * the seam.
 */
class RunControllerTest {

	@Test
	void returnsStructuredErrorWhenNoEndpoint() {
		RunController controller = new RunController((src, goal, input, planner, tier) -> {
			throw new AssertionError("must not run without an endpoint");
		}, new ModelEndpointConfig("", "", "", ""));
		RunResponse response = controller.run(new RunRequest("folder:/tmp", "goal", "input", null, null));
		assertThat(response.errorCode()).isEqualTo("MODEL_NOT_CONFIGURED");
		assertThat(response.error()).contains("No resolvable model endpoint");
		assertThat(response.success()).isFalse();
	}

	@Test
	void returnsErrorWhenNoSource() {
		RunController controller = new RunController((src, goal, input, planner, tier) -> {
			throw new AssertionError("must not run without a source");
		}, configured());
		RunResponse response = controller.run(new RunRequest("", "goal", "input", null, null));
		assertThat(response.error()).contains("No source");
		assertThat(response.errorCode()).isNull();
	}

	@Test
	void runsAndReturnsOutputWhenConfigured() {
		AtomicReference<TrustTier> seenTier = new AtomicReference<>();
		AtomicReference<String> seenPlanner = new AtomicReference<>();
		RunController controller = new RunController((src, goal, input, planner, tier) -> {
			seenTier.set(tier);
			seenPlanner.set(planner);
			return new AgentSpecRunService.RunResult(true, "the output", null, trace("run-1"));
		}, configured());
		RunResponse response = controller.run(new RunRequest("folder:/tmp", "goal", "input", "utility", "HIGH"));
		assertThat(response.success()).isTrue();
		assertThat(response.output()).isEqualTo("the output");
		assertThat(response.runId()).isEqualTo("run-1");
		assertThat(seenTier.get()).isEqualTo(TrustTier.HIGH);
		assertThat(seenPlanner.get()).isEqualTo("utility");
	}

	@Test
	void defaultsToStandardTierWhenUnspecified() {
		AtomicReference<TrustTier> seenTier = new AtomicReference<>();
		RunController controller = new RunController((src, goal, input, planner, tier) -> {
			seenTier.set(tier);
			return new AgentSpecRunService.RunResult(true, "ok", null, trace("run-2"));
		}, configured());
		controller.run(new RunRequest("folder:/tmp", "goal", "input", null, null));
		assertThat(seenTier.get()).isEqualTo(TrustTier.STANDARD);
	}

	@Test
	void returnsFailureWhenRunFails() {
		RunController controller = new RunController((src, goal, input, planner,
				tier) -> new AgentSpecRunService.RunResult(false, null, "goal not achieved", trace("run-3")),
				configured());
		RunResponse response = controller.run(new RunRequest("folder:/tmp", "goal", "input", null, null));
		assertThat(response.success()).isFalse();
		assertThat(response.error()).isEqualTo("goal not achieved");
		assertThat(response.runId()).isEqualTo("run-3");
	}

	private static ModelEndpointConfig configured() {
		return new ModelEndpointConfig("http://localhost:4000", "key", "gpt", "gpt");
	}

	private static TraceRecord trace(String runId) {
		return new TraceRecord(runId, Instant.now(), null, null, "goal", "input", "GOAP", "STANDARD", List.of(), true,
				null, "ok");
	}

}
