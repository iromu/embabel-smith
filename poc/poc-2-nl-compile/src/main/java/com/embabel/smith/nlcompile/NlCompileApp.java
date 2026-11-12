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

package com.embabel.smith.nlcompile;

import java.util.List;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.spec.compile.AgentCompilationResult;
import com.embabel.agent.spec.model.PromptedActionSpec;
import com.embabel.common.ai.model.LlmOptions;
import com.embabel.smith.common.config.ModelEndpointConfig;
import com.embabel.smith.common.model.DraftQuality;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * POC 2 launcher: compiles a natural-language description into a draft spec and reports
 * it.
 *
 * <p>
 * Run (with a live OpenAI-compatible endpoint): <pre>
 *   export OPENAI_CUSTOM_BASE_URL=... OPENAI_CUSTOM_API_KEY=... OPENAI_CUSTOM_MODELS=... EMBABEL_MODELS_DEFAULT_LLM=...
 *   mvn -pl poc/poc-2-nl-compile spring-boot:run \
 *       -Dbuilder.description="An agent that researches a topic and writes a short digest"
 * </pre>
 *
 * @author Ivan Rodriguez
 */
@SpringBootApplication
public class NlCompileApp implements CommandLineRunner {

	private final Ai ai;

	private final AgentPlatform platform;

	private final ModelEndpointConfig endpoint;

	private final String description;

	private final String agentName;

	public NlCompileApp(Ai ai, AgentPlatform platform, @Value("${OPENAI_CUSTOM_BASE_URL:}") String baseUrl,
			@Value("${OPENAI_CUSTOM_API_KEY:}") String apiKey, @Value("${OPENAI_CUSTOM_MODELS:}") String models,
			@Value("${EMBABEL_MODELS_DEFAULT_LLM:}") String defaultLlm,
			@Value("${builder.description:}") String description,
			@Value("${builder.agent-name:draft-agent}") String agentName) {
		this.ai = ai;
		this.platform = platform;
		this.endpoint = new ModelEndpointConfig(baseUrl, apiKey, models, defaultLlm);
		this.description = description;
		this.agentName = agentName;
	}

	public static void main(String[] args) {
		SpringApplication.run(NlCompileApp.class, args);
	}

	@Override
	public void run(String... args) {
		String configError = this.endpoint.configError();
		if (configError != null) {
			throw new IllegalStateException(configError);
		}
		if (this.description == null || this.description.isBlank()) {
			throw new IllegalStateException("No description supplied; set -Dbuilder.description=\"...\"");
		}

		AgentCompilationResult result = new NlCompileRunner(this.ai, this.platform, LlmOptions.withDefaultLlm())
			.compile(this.agentName, this.description);

		System.out.println("=== NL compilation result for '" + this.agentName + "' ===");
		System.out.println("Actions (" + result.getActions().size() + "):");
		for (PromptedActionSpec action : result.getActions()) {
			System.out.println("  - " + action.getName() + ": " + action.getInputTypeNames() + " -> "
					+ action.getOutputTypeName());
		}
		System.out.println("Goal: " + ((result.getGoal() == null) ? "(none)" : result.getGoal().getName()
				+ " (export=" + result.getGoal().getExport() + ", output=" + result.getGoal().getOutputTypeName()
				+ ")"));

		String goalOutput = (result.getGoal() == null) ? null : result.getGoal().getOutputTypeName();
		List<String> actionOutputs = result.getActions().stream().map(PromptedActionSpec::getOutputTypeName).toList();
		DraftQuality.Assessment quality = DraftQuality.assess(goalOutput, actionOutputs);
		System.out.println("Draft quality: " + quality.verdict() + (quality.isDegenerate() ? " (DEGENERATE)" : " (ok)")
				+ " — " + quality.reason());

		System.out.println("Intermediate types (" + result.getIntermediateTypes().size() + "):");
		result.getIntermediateTypes().forEach((t) -> System.out.println("  - " + t.getName()));
		System.out.println("Issues to review (" + result.getErrors().size() + "):");
		result.getErrors().forEach((e) -> System.out.println("  - " + e.getMessage() + " [" + e.getSource() + "]"));
	}

}
