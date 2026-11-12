/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.embabel.smith.common.config;

import java.util.ArrayList;
import java.util.List;

/**
 * The pluggable, env-driven model endpoint (design decision D4).
 *
 * <p>
 * No endpoint is hardcoded anywhere: the OpenAI-compatible base URL, API key, model list
 * and default LLM all come from environment variables, so every POC can be pointed at
 * LiteLLM / OpenAI / Ollama at run time. Shared by all POC launchers so the "clear
 * configuration error when no endpoint is resolvable" behaviour (rather than an opaque
 * model error) is consistent.
 *
 * @param baseUrl the OpenAI-compatible base URL
 * @param apiKey the API key for the endpoint
 * @param models the comma-separated list of available models
 * @param defaultLlm the default LLM model name
 * @author Ivan Rodriguez
 */
public record ModelEndpointConfig(String baseUrl, String apiKey, String models, String defaultLlm) {

	public boolean isConfigured() {
		return notBlank(this.baseUrl) && notBlank(this.apiKey) && notBlank(this.models);
	}

	/**
	 * A clear configuration error, or {@code null} when the endpoint is fully configured.
	 */
	public String configError() {
		List<String> missing = new ArrayList<>();
		if (!notBlank(this.baseUrl)) {
			missing.add("OPENAI_CUSTOM_BASE_URL");
		}
		if (!notBlank(this.apiKey)) {
			missing.add("OPENAI_CUSTOM_API_KEY");
		}
		if (!notBlank(this.models)) {
			missing.add("OPENAI_CUSTOM_MODELS");
		}
		if (missing.isEmpty()) {
			return null;
		}
		return "No resolvable model endpoint is configured. Missing: " + String.join(", ", missing)
				+ ". Set these environment variables to point at any OpenAI-compatible endpoint "
				+ "(LiteLLM, OpenAI, Ollama, ...) before running.";
	}

	private static boolean notBlank(String s) {
		return s != null && !s.isBlank();
	}
}
