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

package com.embabel.smith.runtime;

import java.nio.file.Path;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.smith.common.config.ModelEndpointConfig;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The fixed no-code runtime launcher (POC 1).
 *
 * <p>
 * Boots an Embabel {@link AgentPlatform}, ingests a agent spec from YAML with zero
 * compilation, and runs a plan over the exported goal. The model endpoint is fully
 * env-driven (see {@link ModelEndpointConfig}); the planner defaults to GOAP and is
 * selectable per run.
 *
 * <p>
 * Run (with a live OpenAI-compatible endpoint): <pre>
 *   export OPENAI_CUSTOM_BASE_URL=http://localhost:4000
 *   export OPENAI_CUSTOM_API_KEY=sk-...
 *   export OPENAI_CUSTOM_MODELS=my-model
 *   export EMBABEL_MODELS_DEFAULT_LLM=my-model
 *   mvn -pl poc/poc-1-no-code-runtime spring-boot:run \
 *       -Dbuilder.input="Explain how a coffee machine works" -Dbuilder.planner=GOAP
 * </pre>
 *
 * @author Ivan Rodriguez
 */
@SpringBootApplication
public class NoCodeRuntime implements CommandLineRunner {

	private final AgentPlatform platform;

	private final ModelEndpointConfig endpoint;

	private final Path packDir;

	private final String input;

	private final String plannerOverride;

	public NoCodeRuntime(AgentPlatform platform, @Value("${OPENAI_CUSTOM_BASE_URL:}") String baseUrl,
			@Value("${OPENAI_CUSTOM_API_KEY:}") String apiKey, @Value("${OPENAI_CUSTOM_MODELS:}") String models,
			@Value("${EMBABEL_MODELS_DEFAULT_LLM:}") String defaultLlm,
			@Value("${builder.pack-dir:agent-specs/explainer}") Path packDir, @Value("${builder.input:}") String input,
			@Value("${builder.planner:}") String plannerOverride) {
		this.platform = platform;
		this.endpoint = new ModelEndpointConfig(baseUrl, apiKey, models, defaultLlm);
		this.packDir = packDir;
		this.input = input;
		this.plannerOverride = plannerOverride;
	}

	public static void main(String[] args) {
		SpringApplication.run(NoCodeRuntime.class, args);
	}

	@Override
	public void run(String... args) {
		String configError = this.endpoint.configError();
		if (configError != null) {
			throw new IllegalStateException(configError);
		}

		String result = new SpecRunner(this.platform).run(this.packDir, this.input, this.plannerOverride);
		System.out.println("=== Spec output ===");
		System.out.println(result);
	}

}
