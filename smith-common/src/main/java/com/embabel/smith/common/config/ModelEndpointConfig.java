/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.common.config;

import java.util.ArrayList;
import java.util.List;

/**
 * The pluggable, env-driven model endpoint (design decision D4).
 *
 * <p>
 * No endpoint is hardcoded anywhere. Three providers are supported and the one a run
 * talks to is <em>detected</em> from the environment rather than configured:
 * <ul>
 * <li>{@link Provider#OPENAI_CUSTOM} — any OpenAI-compatible endpoint (LiteLLM,
 * OpenRouter, Ollama): {@code OPENAI_CUSTOM_BASE_URL} + {@code OPENAI_CUSTOM_API_KEY} +
 * {@code OPENAI_CUSTOM_MODELS};</li>
 * <li>{@link Provider#OPENAI} — OpenAI: {@code OPENAI_API_KEY};</li>
 * <li>{@link Provider#ANTHROPIC} — Anthropic: {@code ANTHROPIC_API_KEY}.</li>
 * </ul>
 * The last two register Embabel's bundled model catalog, so the model is chosen with
 * {@code EMBABEL_MODELS_DEFAULT_LLM} rather than a model list. Detection is first-match
 * in the order above — a machine holding more than one key uses the first of them, and
 * {@link #provider()} reports which one won.
 *
 * <p>
 * Shared by the POC launchers and the studio so the "clear configuration error when no
 * endpoint is resolvable" behaviour (rather than an opaque model error) is consistent.
 *
 * @param provider the provider the endpoint was detected as
 * @param baseUrl the OpenAI-compatible base URL (empty for the catalog providers)
 * @param apiKey the API key for the endpoint
 * @param models the comma-separated list of models the custom endpoint registers (empty
 * for the catalog providers, whose catalog is fixed by Embabel)
 * @param defaultLlm the default LLM model name
 * @author Ivan Rodriguez
 */
public record ModelEndpointConfig(Provider provider, String baseUrl, String apiKey, String models, String defaultLlm) {

	/**
	 * The OpenAI-custom form the POC launchers have always used, so their call sites —
	 * and the env-var names their configuration errors name — are unchanged.
	 */
	public ModelEndpointConfig(String baseUrl, String apiKey, String models, String defaultLlm) {
		this(Provider.OPENAI_CUSTOM, baseUrl, apiKey, models, defaultLlm);
	}

	/**
	 * Detects the provider from the endpoint values a run resolved for each of them,
	 * taking the first provider that shows any sign of being configured.
	 * @param customBaseUrl the resolved {@code OPENAI_CUSTOM_BASE_URL}
	 * @param customApiKey the resolved {@code OPENAI_CUSTOM_API_KEY}
	 * @param customModels the resolved {@code OPENAI_CUSTOM_MODELS}
	 * @param openAiApiKey the resolved {@code OPENAI_API_KEY}
	 * @param anthropicApiKey the resolved {@code ANTHROPIC_API_KEY}
	 * @param defaultLlm the resolved default LLM, whichever way it was configured
	 * @return the endpoint as detected, possibly {@link Provider#NONE}
	 */
	public static ModelEndpointConfig detect(String customBaseUrl, String customApiKey, String customModels,
			String openAiApiKey, String anthropicApiKey, String defaultLlm) {
		if (notBlank(customBaseUrl) || notBlank(customApiKey) || notBlank(customModels)) {
			return new ModelEndpointConfig(customBaseUrl, customApiKey, customModels, defaultLlm);
		}
		if (notBlank(openAiApiKey)) {
			return new ModelEndpointConfig(Provider.OPENAI, "", openAiApiKey, "", defaultLlm);
		}
		if (notBlank(anthropicApiKey)) {
			return new ModelEndpointConfig(Provider.ANTHROPIC, "", anthropicApiKey, "", defaultLlm);
		}
		return new ModelEndpointConfig(Provider.NONE, "", "", "", defaultLlm);
	}

	public boolean isConfigured() {
		return configError() == null;
	}

	/**
	 * A clear configuration error, or {@code null} when the endpoint is fully configured.
	 */
	public String configError() {
		if (this.provider == Provider.NONE) {
			return "No model endpoint is configured. Set one of "
					+ "OPENAI_CUSTOM_BASE_URL + OPENAI_CUSTOM_API_KEY + OPENAI_CUSTOM_MODELS "
					+ "(any OpenAI-compatible endpoint: LiteLLM, OpenRouter, Ollama), "
					+ "OPENAI_API_KEY (OpenAI), or ANTHROPIC_API_KEY (Anthropic), "
					+ "and EMBABEL_MODELS_DEFAULT_LLM to the model to run.";
		}
		List<String> missing = new ArrayList<>();
		for (String required : this.provider.requiredEnvVars()) {
			if (!isSet(required)) {
				missing.add(required);
			}
		}
		if (missing.isEmpty()) {
			return null;
		}
		return "No resolvable model endpoint is configured. Missing: " + String.join(", ", missing)
				+ ". Set these environment variables to point at " + this.provider.describe() + " before running.";
	}

	private boolean isSet(String envVar) {
		return switch (envVar) {
			case "OPENAI_CUSTOM_BASE_URL" -> notBlank(this.baseUrl);
			case "OPENAI_CUSTOM_API_KEY" -> notBlank(this.apiKey);
			case "OPENAI_CUSTOM_MODELS" -> notBlank(this.models);
			case "OPENAI_API_KEY", "ANTHROPIC_API_KEY" -> notBlank(this.apiKey);
			case "EMBABEL_MODELS_DEFAULT_LLM" -> notBlank(this.defaultLlm);
			default -> true;
		};
	}

	/**
	 * The model providers an endpoint can be detected as, each carrying what it needs to
	 * run.
	 */
	public enum Provider {

		/** An OpenAI-compatible endpoint of any kind, addressed by base URL. */
		OPENAI_CUSTOM("openai-custom",
				List.of("OPENAI_CUSTOM_BASE_URL", "OPENAI_CUSTOM_API_KEY", "OPENAI_CUSTOM_MODELS"),
				"any OpenAI-compatible endpoint (LiteLLM, OpenAI, Ollama, ...)"),

		/** OpenAI itself, whose models Embabel registers from its bundled catalog. */
		OPENAI("openai", List.of("OPENAI_API_KEY", "EMBABEL_MODELS_DEFAULT_LLM"),
				"OpenAI, naming one of its registered models in EMBABEL_MODELS_DEFAULT_LLM"),

		/** Anthropic, whose models Embabel registers from its bundled catalog. */
		ANTHROPIC("anthropic", List.of("ANTHROPIC_API_KEY", "EMBABEL_MODELS_DEFAULT_LLM"),
				"Anthropic, naming one of its registered models in EMBABEL_MODELS_DEFAULT_LLM"),

		/** Nothing in the environment names a provider. */
		NONE("none", List.of(), "no endpoint");

		/** The provider name as reported. */
		private final String id;

		/** The environment variables the provider needs to run. */
		private final List<String> requiredEnvVars;

		/** What the provider talks to, for configuration errors. */
		private final String description;

		Provider(String id, List<String> requiredEnvVars, String description) {
			this.id = id;
			this.requiredEnvVars = requiredEnvVars;
			this.description = description;
		}

		public String id() {
			return this.id;
		}

		public List<String> requiredEnvVars() {
			return this.requiredEnvVars;
		}

		public String describe() {
			return this.description;
		}

	}

	private static boolean notBlank(String s) {
		return s != null && !s.isBlank();
	}
}
