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

package com.embabel.smith.skillgen;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Arm 1 — the lightweight transport: a raw OpenAI-compatible chat-completions call (the same
 * wire the studio's single-shot generator and POC 24 use). Endpoint-gated. The skill body and
 * the answers can be long, so the token budget and timeout are generous.
 *
 * @author Ivan Rodriguez
 */
public class RawChatCaller implements ChatCaller {

	private static final int MAX_TOKENS = 4000;

	private static final Duration TIMEOUT = Duration.ofSeconds(180);

	private final String baseUrl;

	private final String apiKey;

	private final String model;

	private final ObjectMapper json = new ObjectMapper();

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

	public RawChatCaller(String baseUrl, String apiKey, String model) {
		this.baseUrl = baseUrl;
		this.apiKey = apiKey;
		this.model = model;
	}

	@Override
	public String call(String system, String user) {
		try {
			ObjectNode body = this.json.createObjectNode();
			body.put("model", this.model);
			body.put("max_tokens", MAX_TOKENS);
			body.put("temperature", 0.2);
			ArrayNode messages = body.putArray("messages");
			if (system != null && !system.isBlank()) {
				ObjectNode sys = messages.addObject();
				sys.put("role", "system");
				sys.put("content", system);
			}
			ObjectNode usr = messages.addObject();
			usr.put("role", "user");
			usr.put("content", user);

			HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(this.baseUrl + "/chat/completions"))
				.timeout(TIMEOUT)
				.header("Authorization", "Bearer " + this.apiKey)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(this.json.writeValueAsString(body), StandardCharsets.UTF_8))
				.build();
			HttpResponse<String> response = this.http.send(request,
					HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() / 100 != 2) {
				throw new IllegalStateException(
						"LLM call failed: HTTP " + response.statusCode() + " " + response.body());
			}
			JsonNode root = this.json.readTree(response.body());
			String content = root.path("choices").path(0).path("message").path("content").asText();
			if (content == null || content.isBlank()) {
				throw new IllegalStateException("LLM call returned an empty completion");
			}
			return content;
		}
		catch (RuntimeException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new IllegalStateException("LLM call failed: " + ex.getMessage(), ex);
		}
	}

}
