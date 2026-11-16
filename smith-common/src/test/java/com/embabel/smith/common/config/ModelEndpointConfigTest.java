/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.common.config;

import com.embabel.smith.common.config.ModelEndpointConfig.Provider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Provider detection for the env-driven model endpoint: which provider the environment
 * selects, what each one still needs, and that the OpenAI-custom form the POC launchers
 * build for themselves is unchanged.
 */
class ModelEndpointConfigTest {

	private static final String CUSTOM = "http://localhost:4000/v1";

	@Test
	void customVarsWinOverTheOtherProvidersKeys() {
		ModelEndpointConfig config = ModelEndpointConfig.detect(CUSTOM, "sk-custom", "gpt-mini", "sk-openai",
				"sk-anthropic", "gpt-mini");
		assertThat(config.provider()).isEqualTo(Provider.OPENAI_CUSTOM);
		assertThat(config.isConfigured()).isTrue();
		assertThat(config.baseUrl()).isEqualTo(CUSTOM);
		assertThat(config.models()).isEqualTo("gpt-mini");
	}

	@Test
	void aCustomEndpointMissingItsKeyNamesOnlyThatVar() {
		ModelEndpointConfig config = ModelEndpointConfig.detect(CUSTOM, "", "gpt-mini", "", "", "gpt-mini");
		assertThat(config.isConfigured()).isFalse();
		assertThat(config.configError()).contains("OPENAI_CUSTOM_API_KEY").doesNotContain("OPENAI_CUSTOM_BASE_URL");
	}

	@Test
	void anOpenAiKeySelectsOpenAi() {
		ModelEndpointConfig config = ModelEndpointConfig.detect("", "", "", "sk-openai", "", "gpt-4.1");
		assertThat(config.provider()).isEqualTo(Provider.OPENAI);
		assertThat(config.isConfigured()).isTrue();
	}

	@Test
	void anOpenAiEndpointWithoutANamedModelIsNotConfigured() {
		ModelEndpointConfig config = ModelEndpointConfig.detect("", "", "", "sk-openai", "", "");
		assertThat(config.isConfigured()).isFalse();
		assertThat(config.configError()).contains("EMBABEL_MODELS_DEFAULT_LLM").doesNotContain("OPENAI_CUSTOM_MODELS");
	}

	@Test
	void anAnthropicKeySelectsAnthropic() {
		ModelEndpointConfig config = ModelEndpointConfig.detect("", "", "", "", "sk-anthropic", "claude-sonnet-4-6");
		assertThat(config.provider()).isEqualTo(Provider.ANTHROPIC);
		assertThat(config.isConfigured()).isTrue();
		assertThat(config.configError()).isNull();
	}

	@Test
	void anAnthropicEndpointWithoutANamedModelPointsAtAnthropic() {
		ModelEndpointConfig config = ModelEndpointConfig.detect("", "", "", "", "sk-anthropic", "");
		assertThat(config.configError()).contains("Anthropic")
			.startsWith("No resolvable model endpoint is configured. Missing: EMBABEL_MODELS_DEFAULT_LLM.");
	}

	@Test
	void noProviderAtAllOffersAllThreeOptions() {
		ModelEndpointConfig config = ModelEndpointConfig.detect("", "", "", "", "", "");
		assertThat(config.provider()).isEqualTo(Provider.NONE);
		assertThat(config.isConfigured()).isFalse();
		assertThat(config.configError()).contains("OPENAI_CUSTOM_BASE_URL")
			.contains("OPENAI_API_KEY")
			.contains("ANTHROPIC_API_KEY");
	}

	@Test
	void theCustomFormThePocLaunchersBuildStillReportsOpenAiCustom() {
		ModelEndpointConfig config = new ModelEndpointConfig(CUSTOM, "", "m1", "m1");
		assertThat(config.provider()).isEqualTo(Provider.OPENAI_CUSTOM);
		assertThat(config.isConfigured()).isFalse();
		assertThat(config.configError()).startsWith("No resolvable model endpoint is configured")
			.contains("OPENAI_CUSTOM_API_KEY")
			.doesNotContain("OPENAI_CUSTOM_BASE_URL");
	}

}
