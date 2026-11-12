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

package com.embabel.smith.builder.eval;

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
 * The production {@link PromptRewriter}: asks the model (via the OpenAI-compatible
 * chat-completions endpoint) to revise an action prompt so it satisfies the failing eval
 * criteria (POC 9, the SkillAxe pattern). Endpoint-gated — the call hits the model.
 *
 * @author Ivan Rodriguez
 */
public class LlmPromptRewriter implements PromptRewriter {

	private final String baseUrl;

	private final String apiKey;

	private final String model;

	private final ObjectMapper json = new ObjectMapper();

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

	public LlmPromptRewriter(String baseUrl, String apiKey, String model) {
		this.baseUrl = baseUrl;
		this.apiKey = apiKey;
		this.model = model;
	}

	@Override
	public String rewrite(String currentPrompt, String failureFeedback) {
		String instruction = """
				You revise the prompt of a single spec action so it reliably produces output that
				satisfies the failing acceptance criteria below. Keep it a single action prompt;
				preserve the {{...}} template variables; return ONLY the revised prompt text, no commentary.

				Failing eval tasks:
				%s

				Current prompt:
				%s
				""".formatted(failureFeedback, currentPrompt);
		try {
			ObjectNode body = this.json.createObjectNode();
			body.put("model", this.model);
			ArrayNode messages = body.putArray("messages");
			ObjectNode sys = messages.addObject();
			sys.put("role", "system");
			sys.put("content", "You are an expert at writing precise LLM action prompts.");
			ObjectNode user = messages.addObject();
			user.put("role", "user");
			user.put("content", instruction);

			HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(this.baseUrl + "/chat/completions"))
				.timeout(Duration.ofSeconds(120))
				.header("Authorization", "Bearer " + this.apiKey)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers
						.ofString(this.json.writeValueAsString(body), StandardCharsets.UTF_8))
				.build();
			HttpResponse<String> response = this.http.send(request,
					HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() / 100 != 2) {
				throw new IllegalStateException(
						"Rewrite LLM call failed: HTTP " + response.statusCode() + " " + response.body());
			}
			JsonNode root = this.json.readTree(response.body());
			String content = root.path("choices").path(0).path("message").path("content").asText();
			return (content == null || content.isBlank()) ? currentPrompt : content.trim();
		}
		catch (RuntimeException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new IllegalStateException("Rewrite LLM call failed: " + ex.getMessage(), ex);
		}
	}

}
