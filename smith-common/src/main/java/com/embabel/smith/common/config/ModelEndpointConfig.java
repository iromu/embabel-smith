package com.embabel.smith.common.config;

import java.util.ArrayList;
import java.util.List;

/**
 * The pluggable, env-driven model endpoint (design decision D4).
 *
 * <p>No endpoint is hardcoded anywhere: the OpenAI-compatible base URL, API key, model list and
 * default LLM all come from environment variables, so every POC can be pointed at LiteLLM /
 * OpenAI / Ollama at run time. Shared by all POC launchers so the "clear configuration error when
 * no endpoint is resolvable" behaviour (rather than an opaque model error) is consistent.
 */
public record ModelEndpointConfig(String baseUrl, String apiKey, String models, String defaultLlm) {

    public boolean isConfigured() {
        return notBlank(baseUrl) && notBlank(apiKey) && notBlank(models);
    }

    /** A clear configuration error, or {@code null} when the endpoint is fully configured. */
    public String configError() {
        List<String> missing = new ArrayList<>();
        if (!notBlank(baseUrl)) {
            missing.add("OPENAI_CUSTOM_BASE_URL");
        }
        if (!notBlank(apiKey)) {
            missing.add("OPENAI_CUSTOM_API_KEY");
        }
        if (!notBlank(models)) {
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
