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

package com.embabel.smith.builder;

import java.util.Map;

import com.embabel.agent.api.common.PlannerType;
import com.embabel.agent.api.common.scope.AgentScopeBuilder;
import com.embabel.agent.core.Agent;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.AgentScope;
import com.embabel.agent.core.ProcessOptions;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.smith.common.config.ModelEndpointConfig;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * POC 3 launcher — runs the builder agent end-to-end against a live model endpoint.
 *
 * <p>
 * Boots an Embabel {@link AgentPlatform}, runs the {@link BuilderAgent} (GOAP) over a
 * natural-language description, and reports the agent spec it wrote to disk. The model
 * endpoint is fully env-driven (see {@link ModelEndpointConfig}, design D4).
 *
 * <p>
 * Run (with a live OpenAI-compatible endpoint): <pre>
 *   export OPENAI_CUSTOM_BASE_URL=http://localhost:4000
 *   export OPENAI_CUSTOM_API_KEY=sk-...
 *   export OPENAI_CUSTOM_MODELS=my-model
 *   export EMBABEL_MODELS_DEFAULT_LLM=my-model
 *   mvn -pl poc/poc-3-builder-agent spring-boot:run \
 *       -Dbuilder.description="A spec that explains a topic in plain language and posts it as a background message"
 * </pre>
 *
 * @author Ivan Rodriguez
 */
@SpringBootApplication
public class BuilderApp implements CommandLineRunner {

	private final AgentPlatform platform;

	private final BuilderAgent builderAgent;

	private final ModelEndpointConfig endpoint;

	private final String description;

	public BuilderApp(AgentPlatform platform, BuilderAgent builderAgent,
			@Value("${OPENAI_CUSTOM_BASE_URL:}") String baseUrl, @Value("${OPENAI_CUSTOM_API_KEY:}") String apiKey,
			@Value("${OPENAI_CUSTOM_MODELS:}") String models,
			@Value("${EMBABEL_MODELS_DEFAULT_LLM:}") String defaultLlm,
			@Value("${builder.description:}") String description) {
		this.platform = platform;
		this.builderAgent = builderAgent;
		this.endpoint = new ModelEndpointConfig(baseUrl, apiKey, models, defaultLlm);
		this.description = description;
	}

	public static void main(String[] args) {
		SpringApplication.run(BuilderApp.class, args);
	}

	@Override
	public void run(String... args) {
		String configError = this.endpoint.configError();
		if (configError != null) {
			throw new IllegalStateException(configError);
		}
		String desc = this.description.isBlank()
				? "A spec that takes a user's question, extracts the topic, and posts a plain-language "
						+ "explanation as a background message."
				: this.description;
		System.out.println("[builder] Building a spec from: " + desc);

		AgentScope scope = AgentScopeBuilder.fromInstance(this.builderAgent).createAgentScope();
		Agent agent = scope.createAgent("builder", "builder-agent",
				"Compiles a natural-language description into a validated agent spec on disk");
		this.platform.deploy(agent);

		AgentProcess process = this.platform.runAgentFrom(agent, ProcessOptions.DEFAULT.withPlannerType(PlannerType.GOAP),
				Map.of("userInput", new UserInput(desc)));

		Object last = process.lastResult();
		if (last instanceof AgentSpecRef ref) {
			System.out
				.println("[builder] Built agent spec: " + ref.name() + " v" + ref.version() + " at " + ref.path());
		}
		else {
			System.out.println("[builder] No agent spec produced; status=" + process.getStatus());
		}
	}

}
