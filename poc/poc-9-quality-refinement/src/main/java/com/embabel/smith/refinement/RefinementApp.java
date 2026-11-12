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

import com.embabel.agent.core.AgentPlatform;
import com.embabel.smith.common.config.ModelEndpointConfig;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The eval-guided refinement launcher (POC 9). Evaluates the weak LLM-authored tagline
 * spec, rewrites its prompt from the failing eval tasks, re-evaluates, and reports the
 * before/after composite score (the SpecAxe pattern, doc 03 §5). Endpoint-gated; the
 * model endpoint is fully env-driven.
 *
 * <p>
 * Run (with a live OpenAI-compatible endpoint): <pre>
 *   export OPENAI_CUSTOM_BASE_URL=http://localhost:4000
 *   export OPENAI_CUSTOM_API_KEY=sk-...
 *   export OPENAI_CUSTOM_MODELS=my-model
 *   export EMBABEL_MODELS_DEFAULT_LLM=my-model
 *   mvn -pl poc/poc-9-quality-refinement spring-boot:run
 * </pre>
 *
 * @author Ivan Rodriguez
 */
@SpringBootApplication
public class RefinementApp implements CommandLineRunner {

	private final AgentPlatform platform;

	private final ModelEndpointConfig endpoint;

	public RefinementApp(AgentPlatform platform, @Value("${OPENAI_CUSTOM_BASE_URL:}") String baseUrl,
			@Value("${OPENAI_CUSTOM_API_KEY:}") String apiKey, @Value("${OPENAI_CUSTOM_MODELS:}") String models,
			@Value("${EMBABEL_MODELS_DEFAULT_LLM:}") String defaultLlm) {
		this.platform = platform;
		this.endpoint = new ModelEndpointConfig(baseUrl, apiKey, models, defaultLlm);
	}

	public static void main(String[] args) {
		SpringApplication.run(RefinementApp.class, args);
	}

	@Override
	public void run(String... args) {
		String configError = this.endpoint.configError();
		if (configError != null) {
			throw new IllegalStateException(configError);
		}

		System.out.println("=== POC 9: LLM-authored spec quality + eval-guided refinement ===");
		RefinementRunner runner = new RefinementRunner(this.platform);
		RefinementRunner.Endpoint endpoint = new RefinementRunner.Endpoint(baseUrl(), apiKey(), model());

		RefinementRunner.RefinementOutcome outcome = runner.improve(TaglineSpec.weak(), TaglineSpec.STEP_NAME,
				TaglineSpec.tasks(), endpoint);

		System.out.printf("before score: %.1f%%%n", outcome.beforeScore() * 100);
		if (!outcome.failingInputs().isEmpty()) {
			System.out.println("failing tasks before: " + outcome.failingInputs().size());
		}
		System.out.println("revised prompt:");
		System.out.println(indent(outcome.revisedPrompt()));
		System.out.printf("after score:  %.1f%%%n", outcome.afterScore() * 100);
		System.out.println(outcome.improved() ? "RESULT: the eval-guided rewrite improved the spec."
				: "RESULT: no improvement this run (LLM variance); the loop ran end-to-end.");
	}

	private String baseUrl() {
		return System.getenv().getOrDefault("OPENAI_CUSTOM_BASE_URL", "");
	}

	private String apiKey() {
		return System.getenv().getOrDefault("OPENAI_CUSTOM_API_KEY", "");
	}

	private String model() {
		return System.getenv().getOrDefault("OPENAI_CUSTOM_MODELS", "");
	}

	private String indent(String s) {
		if (s == null) {
			return "  (none)";
		}
		return "  " + s.replace("\n", "\n  ");
	}

}
