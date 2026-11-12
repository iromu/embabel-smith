package com.embabel.smith.studio;

import com.embabel.smith.common.config.ModelEndpointConfig;
import com.embabel.smith.studio.rest.BuildController;
import com.embabel.smith.studio.rest.RunController;
import com.embabel.smith.studio.rest.dto.BuildRequest;
import com.embabel.smith.studio.rest.dto.BuildResponse;
import com.embabel.smith.studio.rest.dto.RunRequest;
import com.embabel.smith.studio.rest.dto.RunResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Endpoint-gating (change 06, task 2.6): the studio boots with no model endpoint
 * configured, and a model-backed action returns the structured
 * {@code MODEL_NOT_CONFIGURED} error rather than crashing. The {@code @Primary} empty
 * endpoint makes the assertion deterministic regardless of the ambient environment; the
 * coherent dummy platform config (matching model + default LLM) lets the Embabel platform
 * boot even when the real endpoint env is absent or set to something else.
 */
@SpringBootTest(properties = { "OPENAI_CUSTOM_BASE_URL=http://127.0.0.1:9/v1", "OPENAI_CUSTOM_API_KEY=sk-studio-test",
		"OPENAI_CUSTOM_MODELS=studio-test-model", "embabel.models.default-llm=studio-test-model", })
class ModelNotConfiguredTest {

	@Autowired
	RunController runController;

	@Autowired
	BuildController buildController;

	@Test
	void runReportsModelNotConfiguredWhenNoEndpoint() {
		RunResponse response = runController.run(new RunRequest("folder:/tmp", "g", "x", null, null));
		assertThat(response.errorCode()).isEqualTo("MODEL_NOT_CONFIGURED");
		assertThat(response.error()).contains("No resolvable model endpoint");
		assertThat(response.success()).isFalse();
	}

	@Test
	void buildReportsModelNotConfiguredWhenNoEndpoint() {
		BuildResponse response = buildController.build(new BuildRequest("a spec", "folder:/tmp/out", null, null, null));
		assertThat(response.errorCode()).isEqualTo("MODEL_NOT_CONFIGURED");
		assertThat(response.status()).isEqualTo("BLOCKED");
	}

	@TestConfiguration
	static class NoEndpoint {

		@Bean
		@Primary
		ModelEndpointConfig emptyEndpoint() {
			return new ModelEndpointConfig("", "", "", "");
		}

	}

}
