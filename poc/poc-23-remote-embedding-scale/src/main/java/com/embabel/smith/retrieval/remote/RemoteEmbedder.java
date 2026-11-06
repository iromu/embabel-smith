package com.embabel.smith.retrieval.remote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A real <em>remote</em> {@code EmbeddingService}: dense embeddings from an OpenAI-compatible
 * {@code POST /embeddings} endpoint. Here that is the {@code nomic-embed-text-v2} llama.cpp server
 * (proxied by LiteLLM). This is the production-style backend doc 03 §2.1 calls for — a genuine
 * network round-trip, in contrast to POC 20's local bundled ONNX MiniLM.
 *
 * <p>The HTTP transport is injectable ({@link EmbeddingTransport}) so offline tests exercise the
 * request/response handling against a stub (or a local server) without the real endpoint.
 */
public final class RemoteEmbedder implements AutoCloseable {

    /** The injectable HTTP transport: POST a JSON body to the embeddings endpoint, return the body. */
    @FunctionalInterface
    public interface EmbeddingTransport {
        String post(String jsonBody) throws IOException;
    }

    private final String model;
    private final EmbeddingTransport transport;
    private final HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicLong requests = new AtomicLong();

    private RemoteEmbedder(String model, EmbeddingTransport transport, HttpClient client) {
        this.model = model;
        this.transport = transport;
        this.client = client;
    }

    /** Build an embedder that talks to {@code baseUrl} over HTTP with {@code apiKey}. */
    public static RemoteEmbedder create(String baseUrl, String apiKey, String model) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        return new RemoteEmbedder(model, httpTransport(client, baseUrl, apiKey), client);
    }

    /** Build an embedder with a custom transport (tests). */
    public static RemoteEmbedder withTransport(String model, EmbeddingTransport transport) {
        return new RemoteEmbedder(model, transport, null);
    }

    @Override
    public void close() {
        if (client != null) {
            client.close();
        }
    }

    /** The model id sent in every request. */
    public String model() {
        return model;
    }

    /** Total embedding HTTP requests issued so far (one per {@link #embedMany} call). */
    public long requestCount() {
        return requests.get();
    }

    /** A dense embedding for a single piece of text. */
    public double[] embed(String text) {
        return embedMany(List.of(text))[0];
    }

    /**
     * Embed a batch of texts in a single request. Returns one vector per text, in input order.
     * The vectors are the raw endpoint output (not re-normalised here — cosine is scale-invariant).
     */
    public double[][] embedMany(List<String> texts) {
        requests.incrementAndGet();
        try {
            ObjectNode req = mapper.createObjectNode();
            req.put("model", model);
            ArrayNode input = req.putArray("input");
            for (String t : texts) {
                input.add(t);
            }
            String resp = transport.post(mapper.writeValueAsString(req));
            JsonNode data = mapper.readTree(resp).get("data");
            if (data == null || !data.isArray()) {
                throw new IllegalStateException("embedding response had no 'data' array: " + resp);
            }
            double[][] out = new double[texts.size()][];
            for (JsonNode item : data) {
                int idx = item.get("index").asInt();
                JsonNode emb = item.get("embedding");
                double[] v = new double[emb.size()];
                for (int i = 0; i < v.length; i++) {
                    v[i] = emb.get(i).asDouble();
                }
                out[idx] = v;
            }
            return out;
        } catch (IOException e) {
            throw new IllegalStateException("embedding request failed: " + e.getMessage(), e);
        }
    }

    /** The default transport: a JDK {@link HttpClient} POST to {@code {baseUrl}/embeddings}. */
    static EmbeddingTransport httpTransport(HttpClient client, String baseUrl, String apiKey) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        URI uri = URI.create(base + "/embeddings");
        return json -> {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            try {
                HttpResponse<String> res = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() / 100 != 2) {
                    throw new IOException(
                            "embedding endpoint returned HTTP " + res.statusCode() + ": " + res.body());
                }
                return res.body();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("embedding request interrupted", e);
            }
        };
    }
}
