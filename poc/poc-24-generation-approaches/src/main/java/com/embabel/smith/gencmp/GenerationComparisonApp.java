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

package com.embabel.smith.gencmp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.smith.common.config.ModelEndpointConfig;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * POC 24 launcher: runs both generation approaches (the studio's single-shot prompt and Embabel's
 * {@code embabel-agent-spec} NL compiler), in flat and multi-agent modes, over the example
 * business-process blueprints, and reports a comparison.
 *
 * <p>
 * Run (with a live OpenAI-compatible endpoint): <pre>
 *   export OPENAI_CUSTOM_BASE_URL=... OPENAI_CUSTOM_API_KEY=... OPENAI_CUSTOM_MODELS=... EMBABEL_MODELS_DEFAULT_LLM=...
 *   mvn -pl poc/poc-24-generation-approaches spring-boot:run
 * </pre>
 * Filter the matrix with {@code -Dcmp.blueprints=TradingAgents}, {@code -Dcmp.modes=multi}, or
 * {@code -Dcmp.examples=/path/to/examples/blueprints}.
 *
 * @author Ivan Rodriguez
 */
@SpringBootApplication
public class GenerationComparisonApp implements CommandLineRunner {

	private final Ai ai;

	private final AgentPlatform platform;

	private final ModelEndpointConfig endpoint;

	private final String baseUrl;

	private final String apiKey;

	private final String model;

	private final String examplesDir;

	private final String blueprints;

	private final String modes;

	public GenerationComparisonApp(Ai ai, AgentPlatform platform,
			@Value("${OPENAI_CUSTOM_BASE_URL:}") String baseUrl, @Value("${OPENAI_CUSTOM_API_KEY:}") String apiKey,
			@Value("${OPENAI_CUSTOM_MODELS:}") String models, @Value("${EMBABEL_MODELS_DEFAULT_LLM:}") String defaultLlm,
			@Value("${cmp.examples:../../examples/blueprints}") String examplesDir,
			@Value("${cmp.blueprints:Aurora,Sentinel,TradingAgents}") String blueprints,
			@Value("${cmp.modes:flat,multi}") String modes) {
		this.ai = ai;
		this.platform = platform;
		this.endpoint = new ModelEndpointConfig(baseUrl, apiKey, models, defaultLlm);
		this.baseUrl = baseUrl;
		this.apiKey = apiKey;
		this.model = defaultLlm;
		this.examplesDir = examplesDir;
		this.blueprints = blueprints;
		this.modes = modes;
	}

	public static void main(String[] args) {
		SpringApplication.run(GenerationComparisonApp.class, args);
	}

	@Override
	public void run(String... args) {
		String configError = this.endpoint.configError();
		if (configError != null) {
			System.err.println(configError);
			return;
		}

		LlmCaller caller = new OpenAiLlmCaller(this.baseUrl, this.apiKey, this.model);
		SingleShotGenerator singleShot = new SingleShotGenerator(caller, loadPrompt("process_to_flow_flat.jinja"),
				loadPrompt("process_to_flow_multi.jinja"));
		EmbabelGenerator embabel = new EmbabelGenerator(this.ai, this.platform, caller,
				loadPrompt("agent_decomposition.jinja"));

		List<String> report = new ArrayList<>();
		report.add("# POC 24 — generation approaches comparison");
		report.add("");
		for (String blueprint : split(this.blueprints)) {
			String text = readBlueprint(blueprint);
			report.add("## Blueprint: " + blueprint + " (" + text.length() + " chars)");
			report.add("");
			for (String mode : split(this.modes)) {
				report.addAll(runSingleShot(blueprint, mode, text, singleShot));
				report.addAll(runEmbabel(blueprint, mode, text, embabel));
			}
		}

		String output = String.join("\n", report);
		System.out.println(output);
		writeResults(output);
	}

	private List<String> runSingleShot(String blueprint, String mode, String text, SingleShotGenerator generator) {
		ProcessSpec process = (multi(mode)) ? generator.generateMulti(text) : generator.generateFlat(text);
		return render(blueprint + " / single-shot " + mode, process);
	}

	private List<String> runEmbabel(String blueprint, String mode, String text, EmbabelGenerator generator) {
		ProcessSpec process = (multi(mode)) ? generator.generateMulti(blueprint, text)
				: generator.generateFlat(blueprint, text);
		return render(blueprint + " / embabel " + mode, process);
	}

	private static boolean multi(String mode) {
		return mode.equals("multi");
	}

	private static List<String> render(String label, ProcessSpec process) {
		try {
			return ComparisonReport.render(label, process, ProcessValidator.validate(process));
		}
		catch (Exception ex) {
			List<String> lines = new ArrayList<>();
			lines.add("=== " + label + " FAILED ===");
			lines.add("  ! " + ((ex.getMessage() == null) ? ex.toString() : ex.getMessage()));
			lines.add("");
			return lines;
		}
	}

	/**
	 * Concatenates every markdown file in the blueprint's directory into one description.
	 * @param name the blueprint directory name.
	 * @return the concatenated blueprint text.
	 */
	private String readBlueprint(String name) {
		Path dir = Path.of(this.examplesDir, name);
		try (Stream<Path> files = Files.list(dir)) {
			StringBuilder sb = new StringBuilder();
			files.filter((p) -> p.toString().endsWith(".md")).sorted()
				.forEach((p) -> {
					try {
						sb.append("### ").append(p.getFileName()).append("\n").append(Files.readString(p, StandardCharsets.UTF_8))
							.append("\n\n");
					}
					catch (IOException ex) {
						throw new IllegalStateException("Failed to read blueprint file " + p, ex);
					}
				});
			if (sb.isEmpty()) {
				throw new IllegalStateException("No markdown files found for blueprint '" + name + "' in " + dir);
			}
			return sb.toString();
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to list blueprint directory " + dir, ex);
		}
	}

	/**
	 * Loads a prompt resource from the classpath.
	 * @param name the prompt file name under {@code /prompts/}.
	 * @return the prompt text.
	 */
	private static String loadPrompt(String name) {
		try (InputStream in = GenerationComparisonApp.class.getResourceAsStream("/prompts/" + name)) {
			if (in == null) {
				throw new IllegalStateException("Prompt resource not found: " + name);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to load prompt " + name, ex);
		}
	}

	private static List<String> split(String csv) {
		List<String> out = new ArrayList<>();
		for (String part : csv.split(",")) {
			if (!part.isBlank()) {
				out.add(part.trim());
			}
		}
		return out;
	}

	private void writeResults(String output) {
		try {
			Path dir = Path.of("results");
			Files.createDirectories(dir);
			Files.writeString(dir.resolve("comparison.md"), output, StandardCharsets.UTF_8);
			System.out.println("\nWrote " + dir.toAbsolutePath().resolve("comparison.md"));
		}
		catch (IOException ex) {
			System.err.println("Failed to write results: " + ex.getMessage());
		}
	}

}
