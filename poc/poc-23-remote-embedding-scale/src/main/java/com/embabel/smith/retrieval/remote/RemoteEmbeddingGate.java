package com.embabel.smith.retrieval.remote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The endpoint gate for the POC 23 launcher: the live benchmark runs only when the embedding
 * endpoint is reachable and serves the requested model. Each missing piece fails with a clear,
 * actionable message rather than an opaque network error.
 */
public final class RemoteEmbeddingGate {

    /** The gate outcome: ready, or a failure message naming the missing piece. */
    public record Result(boolean ready, String failure) {
    }

    /** A model-list probe (injectable for tests). */
    @FunctionalInterface
    public interface EndpointProbe {
        /** The model ids the endpoint serves; throw when the endpoint is unreachable. */
        List<String> listModels() throws Exception;
    }

    /** Gate on the endpoint being reachable and serving {@code model}. */
    public static Result check(EndpointProbe probe, String model) {
        List<String> models;
        try {
            models = probe.listModels();
        } catch (Exception e) {
            return new Result(false,
                    "Embedding endpoint not reachable (" + e.getMessage() + "). "
                            + "Confirm the embeddings server is up and the base URL is correct, then re-run.");
        }
        if (models == null || models.isEmpty()) {
            return new Result(false,
                    "Embedding endpoint is reachable but lists no models. Check the endpoint configuration.");
        }
        if (!models.contains(model)) {
            return new Result(false,
                    "Model '" + model + "' is not served by the endpoint (found: " + models + "). "
                            + "Set EMBED_MODEL to one of the listed models.");
        }
        return new Result(true, null);
    }

    /** The default probe: {@code GET {baseUrl}/models} and collect the model ids. */
    public static EndpointProbe httpModels(String baseUrl, String apiKey) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        URI uri = URI.create(base + "/models");
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        ObjectMapper mapper = new ObjectMapper();
        return () -> {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .build();
            HttpResponse<String> res = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                throw new IllegalStateException("HTTP " + res.statusCode());
            }
            List<String> ids = new ArrayList<>();
            JsonNode data = mapper.readTree(res.body()).get("data");
            if (data != null && data.isArray()) {
                for (JsonNode m : data) {
                    ids.add(m.get("id").asText());
                }
            }
            return ids;
        };
    }

    private RemoteEmbeddingGate() {
    }
}
