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

package com.embabel.smith.adapter;

import java.nio.file.Path;
import java.util.List;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.smith.common.config.ModelEndpointConfig;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The spec runtime adapter launcher (POC 7). Boots an Embabel {@link AgentPlatform}
 * (which registers the {@code shell} tool group bean), ingests a CLI agent spec, and runs
 * it so the spec executes a real command via the injected shell tool. The model endpoint
 * is fully env-driven.
 *
 * <p>
 * Run (with a live OpenAI-compatible endpoint): <pre>
 *   export OPENAI_CUSTOM_BASE_URL=http://localhost:4000
 *   export OPENAI_CUSTOM_API_KEY=sk-...
 *   export OPENAI_CUSTOM_MODELS=my-model
 *   export EMBABEL_MODELS_DEFAULT_LLM=my-model
 *   BUILDER_PACK_DIR="$PWD/agent-specs/shell-runner" \
 *   BUILDER_INPUT="Run the command: uname -s, and report the result" \
 *       mvn -pl poc/poc-7-runtime-adapter spring-boot:run
 * </pre>
 *
 * @author Ivan Rodriguez
 */
@SpringBootApplication
public class AdapterApp implements CommandLineRunner {

	private final AgentPlatform platform;

	private final ModelEndpointConfig endpoint;

	private final Path packDir;

	private final String input;

	public AdapterApp(AgentPlatform platform, @Value("${OPENAI_CUSTOM_BASE_URL:}") String baseUrl,
			@Value("${OPENAI_CUSTOM_API_KEY:}") String apiKey, @Value("${OPENAI_CUSTOM_MODELS:}") String models,
			@Value("${EMBABEL_MODELS_DEFAULT_LLM:}") String defaultLlm,
			@Value("${builder.pack-dir:agent-specs/shell-runner}") Path packDir,
			@Value("${builder.input:}") String input) {
		this.platform = platform;
		this.endpoint = new ModelEndpointConfig(baseUrl, apiKey, models, defaultLlm);
		this.packDir = packDir;
		this.input = input;
	}

	public static void main(String[] args) {
		SpringApplication.run(AdapterApp.class, args);
	}

	@Override
	public void run(String... args) {
		String configError = this.endpoint.configError();
		if (configError != null) {
			throw new IllegalStateException(configError);
		}
		if (this.input == null || this.input.isBlank()) {
			throw new IllegalStateException("builder.input is required");
		}

		List<String> available = this.platform.getToolGroupResolver()
			.availableToolGroups()
			.stream()
			.map((m) -> m.getRole())
			.sorted()
			.toList();
		System.out.println("=== Registered tool groups: " + available + " ===");
		if (!available.contains(ShellToolGroup.ROLE)) {
			System.out
				.println("WARNING: the 'shell' tool group is not registered; the spec will run without shell access.");
		}

		System.out.println("=== Running CLI spec over: " + this.input + " ===");
		String result = new AdapterRunner(this.platform).run(this.packDir, this.input);
		System.out.println("=== Spec output ===");
		System.out.println(result);
	}

}
