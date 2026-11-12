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

package com.embabel.smith.refinement;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.validation.AgentSpecValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The eval → rewrite step (POC 9, the SpecAxe pattern from doc 03 §5). Two halves:
 * {@link #rewrite} is endpoint-gated (asks the model to revise the prompt so it satisfies
 * the failing criteria); {@link #applyRevisedPrompt} is pure Java (splices the revised
 * prompt into the named step and re-runs validation), so the re-validation half is
 * verifiable offline.
 *
 * @author Ivan Rodriguez
 */
public class PromptRewriter {

	private final AgentSpecValidator validator = new AgentSpecValidator();

	private final ObjectMapper json = new ObjectMapper();

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

	/**
	 * Endpoint-gated: ask the model to revise {@code currentPrompt} so it satisfies the
	 * failing criteria. Uses the OpenAI-compatible chat-completions endpoint directly
	 * (the endpoint is already env-driven for the rest of the POC).
	 * @param baseUrl the OpenAI-compatible endpoint base URL
	 * @param apiKey the API key for the endpoint
	 * @param model the model name to call
	 * @param currentPrompt the current prompt text
	 * @param failingTasks the eval tasks that failed
	 * @return the revised prompt text
	 */
	public String rewrite(String baseUrl, String apiKey, String model, String currentPrompt,
			List<SpecEvalTask> failingTasks) {
		StringBuilder criteria = new StringBuilder();
		for (SpecEvalTask task : failingTasks) {
			criteria.append("- input: ")
				.append(task.input())
				.append("; must satisfy: ")
				.append(task.acceptanceCriteria())
				.append('\n');
		}
		String instruction = """
				You revise the prompt of a single spec action so it reliably produces output that
				satisfies the acceptance criteria below. Keep it a single action prompt; preserve the
				{{...}} template variables; return ONLY the revised prompt text, no commentary.

				Acceptance criteria the current prompt fails:
				%s

				Current prompt:
				%s
				""".formatted(criteria, currentPrompt);

		try {
			ObjectNode body = this.json.createObjectNode();
			body.put("model", model);
			ArrayNode messages = body.putArray("messages");
			ObjectNode sys = messages.addObject();
			sys.put("role", "system");
			sys.put("content", "You are an expert at writing precise LLM action prompts.");
			ObjectNode user = messages.addObject();
			user.put("role", "user");
			user.put("content", instruction);

			HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + "/chat/completions"))
				.timeout(Duration.ofSeconds(120))
				.header("Authorization", "Bearer " + apiKey)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(this.json.writeValueAsString(body), StandardCharsets.UTF_8))
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

	/**
	 * Pure: splice a revised prompt into the named step and re-validate the revised
	 * draft.
	 * @param pack the spec pack to revise
	 * @param stepName the name of the step whose prompt is replaced
	 * @param revisedPrompt the revised prompt text
	 * @return the rewrite result with the revised pack and validation issues
	 */
	public RewriteResult applyRevisedPrompt(AgentSpec pack, String stepName, String revisedPrompt) {
		List<ActionStepSpec> newSteps = pack.steps()
			.stream()
			.map((s) -> s.name().equals(stepName) ? withPrompt(s, revisedPrompt) : s)
			.toList();
		AgentSpec revised = new AgentSpec(pack.manifest(), pack.goal(), newSteps, pack.types());
		List<String> issues = this.validator.validate(revised)
			.issues()
			.stream()
			.map((issue) -> issue.code() + " — " + issue.message())
			.toList();
		return new RewriteResult(revisedPrompt, revised, issues.isEmpty(), issues);
	}

	private ActionStepSpec withPrompt(ActionStepSpec s, String prompt) {
		return new ActionStepSpec(s.stepType(), s.name(), s.description(), s.inputTypeNames(), s.outputTypeName(),
				prompt, s.pre(), s.post(), s.cost(), s.value(), s.canRerun(), s.nullable(), s.toolGroups());
	}

	/**
	 * The result of applying a rewrite: the revised prompt, the revised pack, and whether
	 * it re-validates.
	 *
	 * @param revisedPrompt the revised prompt text
	 * @param revisedPack the revised spec pack
	 * @param valid whether the revised pack re-validates
	 * @param issues the validation issues, empty when valid
	 */
	public record RewriteResult(String revisedPrompt, AgentSpec revisedPack, boolean valid, List<String> issues) {
		public RewriteResult {
			issues = (issues != null) ? List.copyOf(issues) : List.of();
		}
	}

}
