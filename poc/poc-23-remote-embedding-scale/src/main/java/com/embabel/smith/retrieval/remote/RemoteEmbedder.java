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

package com.embabel.smith.retrieval.remote;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A real <em>remote</em> {@code EmbeddingService}: dense embeddings from an
 * OpenAI-compatible {@code POST /embeddings} endpoint. Here that is the
 * {@code nomic-embed-text-v2} llama.cpp server (proxied by LiteLLM). This is the
 * production-style backend doc 03 §2.1 calls for — a genuine network round-trip, in
 * contrast to POC 20's local bundled ONNX MiniLM.
 *
 * <p>
 * The HTTP transport is injectable ({@link EmbeddingTransport}) so offline tests exercise
 * the request/response handling against a stub (or a local server) without the real
 * endpoint.
 *
 * @author Ivan Rodriguez
 */
public final class RemoteEmbedder implements AutoCloseable {

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

	/**
	 * Build an embedder that talks to {@code baseUrl} over HTTP with {@code apiKey}.
	 * @param baseUrl the OpenAI-compatible base URL.
	 * @param apiKey the API key.
	 * @param model the model id.
	 * @return a new embedder talking to the endpoint.
	 */
	public static RemoteEmbedder create(String baseUrl, String apiKey, String model) {
		HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
		return new RemoteEmbedder(model, httpTransport(client, baseUrl, apiKey), client);
	}

	/**
	 * Build an embedder with a custom transport (tests).
	 * @param model the model id.
	 * @param transport the transport to use.
	 * @return a new embedder using the given transport.
	 */
	public static RemoteEmbedder withTransport(String model, EmbeddingTransport transport) {
		return new RemoteEmbedder(model, transport, null);
	}

	@Override
	public void close() {
		if (this.client != null) {
			this.client.close();
		}
	}

	/**
	 * The model id sent in every request.
	 * @return the model id sent in every request.
	 */
	public String model() {
		return this.model;
	}

	/**
	 * Total embedding HTTP requests issued so far (one per {@link #embedMany} call).
	 * @return the total embedding requests issued so far.
	 */
	public long requestCount() {
		return this.requests.get();
	}

	/**
	 * A dense embedding for a single piece of text.
	 * @param text the text to embed.
	 * @return a dense embedding for the text.
	 */
	public double[] embed(String text) {
		return embedMany(List.of(text))[0];
	}

	/**
	 * Embed a batch of texts in a single request. Returns one vector per text, in input
	 * order. The vectors are the raw endpoint output (not re-normalised here — cosine is
	 * scale-invariant).
	 * @param texts the texts to embed.
	 * @return one vector per text, in input order.
	 */
	public double[][] embedMany(List<String> texts) {
		this.requests.incrementAndGet();
		try {
			ObjectNode req = this.mapper.createObjectNode();
			req.put("model", this.model);
			ArrayNode input = req.putArray("input");
			for (String t : texts) {
				input.add(t);
			}
			String resp = this.transport.post(this.mapper.writeValueAsString(req));
			JsonNode data = this.mapper.readTree(resp).get("data");
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
		}
		catch (IOException ex) {
			throw new IllegalStateException("embedding request failed: " + ex.getMessage(), ex);
		}
	}

	/**
	 * The default transport: a JDK {@link HttpClient} POST to
	 * {@code {baseUrl}/embeddings}.
	 * @param client the HTTP client.
	 * @param baseUrl the base URL.
	 * @param apiKey the API key.
	 * @return a transport posting to the endpoint.
	 */
	static EmbeddingTransport httpTransport(HttpClient client, String baseUrl, String apiKey) {
		String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		URI uri = URI.create(base + "/embeddings");
		return (json) -> {
			HttpRequest request = HttpRequest.newBuilder(uri)
				.timeout(Duration.ofSeconds(60))
				.header("Content-Type", "application/json")
				.header("Authorization", "Bearer " + apiKey)
				.POST(HttpRequest.BodyPublishers.ofString(json))
				.build();
			try {
				HttpResponse<String> res = client.send(request, HttpResponse.BodyHandlers.ofString());
				if (res.statusCode() / 100 != 2) {
					throw new IOException("embedding endpoint returned HTTP " + res.statusCode() + ": " + res.body());
				}
				return res.body();
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new IOException("embedding request interrupted", ex);
			}
		};
	}

	/**
	 * The injectable HTTP transport: POST a JSON body to the embeddings endpoint, return
	 * the body.
	 */
	@FunctionalInterface
	public interface EmbeddingTransport {

		String post(String jsonBody) throws IOException;

	}

}
