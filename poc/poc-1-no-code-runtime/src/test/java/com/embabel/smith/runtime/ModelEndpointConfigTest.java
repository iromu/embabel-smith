package com.embabel.smith.runtime;

import com.embabel.smith.common.config.ModelEndpointConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies task 2.4 (offline part): a missing model endpoint yields a clear configuration
 * error that names the missing variables, and a complete endpoint passes.
 */
class ModelEndpointConfigTest {

	@Test
	void reportsMissingVariablesWhenUnconfigured() {
		ModelEndpointConfig config = new ModelEndpointConfig("", "", "", "");

		assertThat(config.isConfigured()).isFalse();
		String error = config.configError();
		assertThat(error).isNotNull();
		assertThat(error).contains("OPENAI_CUSTOM_BASE_URL");
		assertThat(error).contains("OPENAI_CUSTOM_API_KEY");
		assertThat(error).contains("OPENAI_CUSTOM_MODELS");
	}

	@Test
	void reportsOnlyTheMissingVariable() {
		ModelEndpointConfig config = new ModelEndpointConfig("http://localhost:4000", "", "m1", "m1");

		assertThat(config.isConfigured()).isFalse();
		assertThat(config.configError()).contains("OPENAI_CUSTOM_API_KEY");
		assertThat(config.configError()).doesNotContain("OPENAI_CUSTOM_BASE_URL");
	}

	@Test
	void passesWhenFullyConfigured() {
		ModelEndpointConfig config = new ModelEndpointConfig("http://localhost:4000", "sk-x", "m1", "m1");

		assertThat(config.isConfigured()).isTrue();
		assertThat(config.configError()).isNull();
	}

}
