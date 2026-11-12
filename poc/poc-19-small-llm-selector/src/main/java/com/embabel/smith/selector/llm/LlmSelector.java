package com.embabel.smith.selector.llm;

import com.embabel.smith.retrieval.SpecEntry;
import com.embabel.smith.retrieval.gliner.SpecSelector;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * A {@link SpecSelector} backed by the small decision LLM served by the module's Docker
 * container (llama.cpp, OpenAI-compatible {@code /v1}). One instance per
 * {@link DecisionPrompt.Format}: the selector asks the model to choose among the candidates
 * and parses the reply into the top-1 selection. The contract answer is one token, so the
 * request is temperature 0 with a small {@code max_tokens}.
 */
public final class LlmSelector implements SpecSelector {

    /** The model name the container serves (its mounted GGUF path). */
    public static final String DEFAULT_MODEL = "/models/Qwen3-0.6B-openjev-Q8_0.gguf";

    /** The default local endpoint — the compose.yml port mapping. */
    public static final String DEFAULT_BASE_URL = "http://localhost:18081/v1";

    private final String baseUrl;
    private final String model;
    private final DecisionPrompt.Format format;
    private final HttpClient http;
    private final ObjectMapper json;

    public LlmSelector(String baseUrl, String model, DecisionPrompt.Format format) {
        this(baseUrl, model, format, HttpClient.newHttpClient());
    }

    LlmSelector(String baseUrl, String model, DecisionPrompt.Format format, HttpClient http) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model;
        this.format = format;
        this.http = http;
        this.json = new ObjectMapper();
    }

    @Override
    public String select(String query, List<SpecEntry> candidates) {
        if (candidates.isEmpty()) {
            return null;
        }
        String reply = complete(DecisionPrompt.build(format, query, candidates));
        return DecisionPrompt.parse(format, reply, candidates);
    }

    private String complete(String prompt) {
        try {
            ObjectNode body = json.createObjectNode();
            body.put("model", model);
            body.put("temperature", 0);
            body.put("max_tokens", 16);
            ObjectNode message = body.putArray("messages").addObject();
            message.put("role", "user");
            message.put("content", prompt);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException(
                        "model endpoint " + baseUrl + " returned HTTP " + response.statusCode());
            }
            JsonNode root = json.readTree(response.body());
            return root.path("choices").path(0).path("message").path("content").asText("");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while calling the model endpoint", e);
        } catch (Exception e) {
            throw new IllegalStateException("calling the model endpoint " + baseUrl + " failed: " + e, e);
        }
    }
}
