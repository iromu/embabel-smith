package com.embabel.smith.studio.generate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * The production {@link FlowPromptCaller}: a raw OpenAI-compatible chat-completions call (the same
 * wire the builder's {@code LlmPromptRewriter} uses). Endpoint-gated. The generation prompt is
 * large and the answer a full agent spec, so the timeout and token budget are generous.
 */
public class LlmFlowPromptCaller implements FlowPromptCaller {

    private static final int MAX_TOKENS = 12000;
    private static final Duration TIMEOUT = Duration.ofSeconds(300);

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public LlmFlowPromptCaller(String baseUrl, String apiKey, String model) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public String call(String system, String user) {
        try {
            ObjectNode body = json.createObjectNode();
            body.put("model", model);
            body.put("max_tokens", MAX_TOKENS);
            body.put("temperature", 0.2);
            ArrayNode messages = body.putArray("messages");
            ObjectNode sys = messages.addObject();
            sys.put("role", "system");
            sys.put("content", system);
            ObjectNode usr = messages.addObject();
            usr.put("role", "user");
            usr.put("content", user);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Generation LLM call failed: HTTP " + response.statusCode()
                        + " " + response.body());
            }
            JsonNode root = json.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText();
            if (content == null || content.isBlank()) {
                throw new IllegalStateException("Generation LLM call returned an empty completion");
            }
            return content;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Generation LLM call failed: " + e.getMessage(), e);
        }
    }
}
