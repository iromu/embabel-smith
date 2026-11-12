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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The endpoint gate for the POC 23 launcher: the live benchmark runs only when the
 * embedding endpoint is reachable and serves the requested model. Each missing piece
 * fails with a clear, actionable message rather than an opaque network error.
 *
 * @author Ivan Rodriguez
 */
public final class RemoteEmbeddingGate {

	/**
	 * Gate on the endpoint being reachable and serving {@code model}.
	 * @param probe the model-list probe.
	 * @param model the model the endpoint must serve.
	 * @return the gate outcome.
	 */
	public static Result check(EndpointProbe probe, String model) {
		List<String> models;
		try {
			models = probe.listModels();
		}
		catch (Exception ex) {
			return new Result(false, "Embedding endpoint not reachable (" + ex.getMessage() + "). "
					+ "Confirm the embeddings server is up and the base URL is correct, then re-run.");
		}
		if (models == null || models.isEmpty()) {
			return new Result(false,
					"Embedding endpoint is reachable but lists no models. Check the endpoint configuration.");
		}
		if (!models.contains(model)) {
			return new Result(false, "Model '" + model + "' is not served by the endpoint (found: " + models + "). "
					+ "Set EMBED_MODEL to one of the listed models.");
		}
		return new Result(true, null);
	}

	/**
	 * The default probe: {@code GET {baseUrl}/models} and collect the model ids.
	 * @param baseUrl the base URL.
	 * @param apiKey the API key.
	 * @return a probe hitting the endpoint.
	 */
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

	/**
	 * The gate outcome: ready, or a failure message naming the missing piece.
	 * @param ready true when the endpoint is ready.
	 * @param failure the failure message, or null when ready.
	 */
	public record Result(boolean ready, String failure) {
	}

	/**
	 * A model-list probe (injectable for tests).
	 */
	@FunctionalInterface
	public interface EndpointProbe {

		/**
		 * The model ids the endpoint serves; throw when the endpoint is unreachable.
		 * @return the model ids the endpoint serves.
		 */
		List<String> listModels() throws Exception;

	}

}
