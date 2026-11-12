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

package com.embabel.smith.selector.llm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.embabel.smith.retrieval.gliner.DenseSelector;
import com.embabel.smith.retrieval.gliner.GlinerModel;
import com.embabel.smith.retrieval.gliner.GlinerSelector;
import com.embabel.smith.retrieval.gliner.LexicalSelector;
import com.embabel.smith.retrieval.gliner.ShadowingRunner;
import com.embabel.smith.retrieval.gliner.SpecSelector;
import com.embabel.smith.retrieval.scale.ScaleSpecLibrary;
import io.gravitee.lab.gliner4j.schema.ClassificationLabel;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The POC 19 launcher: the model server is a Docker container (llama.cpp serving the
 * Qwen3-0.6B decision model) whose lifecycle Spring Boot Dev Services manages from this
 * module's {@code compose.yml}; the benchmark then scores five methods on POC 17's
 * shadowing scenario — the GLiNER2 transformer, the lexical and dense baselines, and the
 * small LLM under its in-distribution decision prompt ({@code llm-id}) and an
 * out-of-distribution free-form prompt ({@code llm-ood}).
 *
 * <p>
 * Docker-gated: before Spring starts, the launcher checks the weights are present and the
 * Docker daemon is reachable, failing with a clear, actionable error otherwise.
 *
 * <p>
 * Run: {@code mvn -pl poc/poc-19-small-llm-selector spring-boot:run} (from the repo root,
 * weights present).
 *
 * @author Ivan Rodriguez
 */
@SpringBootApplication
public class SmallLlmSelectorApp implements CommandLineRunner {

	private static final String GGUF = "Qwen3-0.6B-openjev-Q8_0.gguf";

	@Value("${llm.base-url:}")
	private String configuredBaseUrl;

	@Value("${llm.model:}")
	private String configuredModel;

	/**
	 * The compose file, relative to the repo root (used when running from outside the
	 * module).
	 */
	private static final String COMPOSE_FROM_REPO_ROOT = "poc/poc-19-small-llm-selector/compose.yml";

	public static void main(String[] args) {
		Path gguf = resolveGguf();
		ServingGate.Result gate = ServingGate.check(gguf, ServingGate.dockerCli());
		if (!gate.ready()) {
			System.err.println("=== POC 19: small-LLM spec selection — cannot start ===");
			System.err.println(gate.failure());
			System.exit(1);
			return;
		}
		// Dev services looks for compose.yml in the working directory; when the app runs
		// from
		// the repo root instead of the module directory, point it at the module's file.
		if (!Files.exists(Path.of("compose.yml")) && Files.exists(Path.of(COMPOSE_FROM_REPO_ROOT))) {
			System.setProperty("spring.docker.compose.file", COMPOSE_FROM_REPO_ROOT);
		}
		SpringApplication.run(SmallLlmSelectorApp.class, args);
	}

	/**
	 * The weights, honoring {@code -Dllm.model-dir} and found from either CWD.
	 * @return the path of the weights file
	 */
	private static Path resolveGguf() {
		String override = System.getProperty("llm.model-dir");
		if (override != null && !override.isBlank()) {
			return Path.of(override, GGUF);
		}
		return resolveExisting(Paths.get("models", GGUF), Paths.get("poc/poc-19-small-llm-selector/models", GGUF));
	}

	/**
	 * The GLiNER2 bundle, found from either the repo root or this module's directory.
	 * @return the GLiNER2 model with the bundle loaded
	 */
	private static GlinerModel glinerModel() {
		Path fromRepoRoot = Path.of("poc/poc-17-gliner-retrieval/models/gliner2-base-v1");
		Path fromModuleDir = Path.of("../poc-17-gliner-retrieval/models/gliner2-base-v1");
		return new GlinerModel(resolveExisting(fromRepoRoot, fromModuleDir)).requireAvailable();
	}

	private static Path resolveExisting(Path first, Path second) {
		return first.toFile().exists() ? first : second;
	}

	/**
	 * The five benchmark methods in table order: the GLiNER2 transformer, the lexical and
	 * dense baselines, and the small LLM under each prompt format.
	 * @param baseUrl the model endpoint base URL
	 * @param model the model name served by the endpoint
	 * @param gliner2 the GLiNER2 transformer selector
	 * @return the methods keyed by their table label
	 */
	static Map<String, SpecSelector> benchmarkMethods(String baseUrl, String model, SpecSelector gliner2) {
		Map<String, SpecSelector> methods = new LinkedHashMap<>();
		methods.put("transformer (GLiNER2)", gliner2);
		methods.put("lexical (word TF)", new LexicalSelector());
		methods.put("dense (feature hash)", new DenseSelector());
		methods.put("llm-id (Qwen3-0.6B)", new LlmSelector(baseUrl, model, DecisionPrompt.Format.ID));
		methods.put("llm-ood (Qwen3-0.6B)", new LlmSelector(baseUrl, model, DecisionPrompt.Format.OOD));
		return methods;
	}

	@Override
	public void run(String... args) {
		System.out.println("=== POC 19: small-LLM spec selection ===");
		System.out.println("A 0.6B model fine-tuned for single-label decisions (iromu/Qwen3-0.6B-openjev),");
		System.out.println("served in Docker (llama.cpp, compose.yml, dev-services managed), as a spec selector");
		System.out.println("on POC 17's shadowing scenario — under its in-distribution decision prompt");
		System.out.println("(llm-id) and an out-of-distribution free-form prompt (llm-ood).");
		System.out.println();

		String baseUrl = this.configuredBaseUrl.isBlank() ? LlmSelector.DEFAULT_BASE_URL : this.configuredBaseUrl;
		String model = this.configuredModel.isBlank() ? LlmSelector.DEFAULT_MODEL : this.configuredModel;
		System.out.println("Model endpoint: " + baseUrl + " (model: " + model + ")");
		System.out.println("Shadowing scenario: " + ScaleSpecLibrary.benchmark().size()
				+ " targets x 2 candidate-set variants (matching + strict)");
		System.out.println();

		List<ClassificationLabel> loadTimeLabels = GlinerSelector.labelsFor(ScaleSpecLibrary.full());
		ShadowingRunner runner = new ShadowingRunner();
		try (GlinerSelector gliner = GlinerSelector.fromModel(glinerModel(), loadTimeLabels)) {
			Map<String, SpecSelector> methods = benchmarkMethods(baseUrl, model, gliner);
			System.out.println("-- Precision@1 per method, per candidate-set variant --");
			runner.printComparisonTable(methods);
		}

		System.out.println();
		System.out.println("matching = canonical + all near-duplicates (POC-12 comparable);");
		System.out.println("strict   = canonical + near-duplicates minus the near-exact duplicate.");
		System.out.println("llm-id   = the model's typed-decision prompt (lettered options, single-letter answer);");
		System.out.println("llm-ood  = free-form prompt (numbered options, 'reply with the number').");
	}

}
