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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.embabel.agent.api.common.Ai;
import com.embabel.smith.common.config.ModelEndpointConfig;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * POC 25 launcher: for each business-process blueprint, generate an Agent Skill
 * ({@code SKILL.md}), load (or draft) its {@code evals.json} suite, then run every eval against
 * a general-purpose agent both with the skill loaded and without it (the baseline) — over two
 * arms side by side (a raw OpenAI-compatible call and the Embabel {@code Ai} runtime) — grade
 * each run against the expectations, and report the with-skill pass rate versus the no-skill
 * baseline.
 *
 * <p>
 * Run (with a live OpenAI-compatible endpoint): <pre>
 *   export OPENAI_CUSTOM_BASE_URL=... OPENAI_CUSTOM_API_KEY=... OPENAI_CUSTOM_MODELS=... EMBABEL_MODELS_DEFAULT_LLM=...
 *   mvn -pl poc/poc-25-skill-generation spring-boot:run
 * </pre>
 * Filter with {@code -Dskillgen.blueprints=TradingAgents}, {@code -Dskillgen.arms=raw}, or
 * {@code -Dskillgen.evalsource=generated}.
 *
 * @author Ivan Rodriguez
 */
@SpringBootApplication
public class SkillGenerationApp implements CommandLineRunner {

	private final Ai ai;

	private final ModelEndpointConfig endpoint;

	private final String baseUrl;

	private final String apiKey;

	private final String model;

	private final String examplesDir;

	private final String blueprints;

	private final String arms;

	private final String evalSource;

	public SkillGenerationApp(Ai ai, @Value("${OPENAI_CUSTOM_BASE_URL:}") String baseUrl,
			@Value("${OPENAI_CUSTOM_API_KEY:}") String apiKey, @Value("${OPENAI_CUSTOM_MODELS:}") String models,
			@Value("${EMBABEL_MODELS_DEFAULT_LLM:}") String defaultLlm,
			@Value("${skillgen.examples:../../examples/blueprints}") String examplesDir,
			@Value("${skillgen.blueprints:Aurora,Sentinel,TradingAgents}") String blueprints,
			@Value("${skillgen.arms:raw,embabel}") String arms, @Value("${skillgen.evalsource:bundled}") String evalSource) {
		this.ai = ai;
		this.endpoint = new ModelEndpointConfig(baseUrl, apiKey, models, defaultLlm);
		this.baseUrl = baseUrl;
		this.apiKey = apiKey;
		this.model = defaultLlm;
		this.examplesDir = examplesDir;
		this.blueprints = blueprints;
		this.arms = arms;
		this.evalSource = evalSource;
	}

	public static void main(String... args) {
		SpringApplication.run(SkillGenerationApp.class, args);
	}

	@Override
	public void run(String... args) {
		String configError = this.endpoint.configError();
		if (configError != null) {
			System.err.println(configError);
			return;
		}

		RawChatCaller raw = new RawChatCaller(this.baseUrl, this.apiKey, this.model);
		EmbabelChatCaller embabel = new EmbabelChatCaller(this.ai);

		String generateSkill = loadPrompt("generate_skill.jinja");
		String generateEvals = loadPrompt("generate_evals.jinja");
		String withSkill = loadPrompt("agent_with_skill.jinja");
		String baseline = loadPrompt("agent_baseline.jinja");
		String grade = loadPrompt("grade_eval.jinja");

		List<String> report = new ArrayList<>();
		report.add("# POC 25 — blueprint → Agent Skill generation + with/without-skill eval");
		report.add("");
		report.add("Each blueprint is turned into a `SKILL.md`; its `evals.json` suite is then run");
		report.add("against a general-purpose agent **with the skill loaded** and **without it** (the");
		report.add("baseline), graded against verifiable expectations. The skill lift is the");
		report.add("with-skill pass rate minus the no-skill pass rate.");
		report.add("");
		for (String blueprint : split(this.blueprints)) {
			try {
				report.addAll(runBlueprint(blueprint, raw, embabel, generateSkill, generateEvals, withSkill, baseline,
						grade));
			}
			catch (Exception ex) {
				report.add("## Blueprint: " + blueprint);
				report.add("");
				report.add("FAILED: " + ex.getMessage());
				report.add("");
			}
		}

		String output = String.join("\n", report);
		System.out.println(output);
		writeResults(output);
	}

	private List<String> runBlueprint(String blueprint, RawChatCaller raw, EmbabelChatCaller embabel,
			String generateSkill, String generateEvals, String withSkill, String baseline, String grade) {
		String text = readBlueprint(blueprint);
		String skillName = slug(blueprint);

		SkillGenerator generator = new SkillGenerator(raw, generateSkill);
		Skill skill = generator.generate(skillName, text);
		List<String> skillIssues = SkillMdValidator.validate(skill.name(), skill.description());

		EvalSuite evals = (this.evalSource.equals("generated"))
				? EvalSource.generate(raw, generateEvals, skillName, text)
				: EvalSource.load(skillName);

		List<ComparisonReport.Arm> arms = new ArrayList<>();
		List<String> armErrors = new ArrayList<>();
		for (String arm : split(this.arms)) {
			try {
				ChatCaller caller = arm.equals("embabel") ? embabel : raw;
				SkillGrader grader = new SkillGrader(caller, grade);
				EvalRunner runner = new EvalRunner(caller, grader, withSkill, baseline);
				List<EvalRunner.EvalRun> runs = runner.run(skill, evals);
				arms.add(new ComparisonReport.Arm(arm, runs, Benchmark.aggregate(runs)));
			}
			catch (Exception ex) {
				armErrors.add(arm + ": " + ex.getMessage());
			}
		}

		List<String> lines = ComparisonReport.render(blueprint, skill, evals, this.evalSource, arms);
		if (!armErrors.isEmpty()) {
			lines.add("Arm errors:");
			for (String error : armErrors) {
				lines.add("  ! " + error);
			}
			lines.add("");
		}
		if (!skillIssues.isEmpty()) {
			lines.add("SKILL.md frontmatter issues:");
			for (String issue : skillIssues) {
				lines.add("  ! " + issue);
			}
			lines.add("");
		}
		writeSkill(blueprint, skill);
		writeEvals(blueprint, evals);
		return lines;
	}

	private void writeSkill(String blueprint, Skill skill) {
		try {
			Path dir = Path.of("results", blueprint, skill.name());
			Files.createDirectories(dir);
			Files.writeString(dir.resolve("SKILL.md"), skill.toSkillMd(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			System.err.println("Failed to write SKILL.md for " + blueprint + ": " + ex.getMessage());
		}
	}

	private void writeEvals(String blueprint, EvalSuite evals) {
		try {
			Path dir = Path.of("results", blueprint);
			Files.createDirectories(dir);
			Files.writeString(dir.resolve("evals.json"), evals.toString(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			System.err.println("Failed to write evals.json for " + blueprint + ": " + ex.getMessage());
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
		try (InputStream in = SkillGenerationApp.class.getResourceAsStream("/prompts/" + name)) {
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

	private static String slug(String name) {
		String s = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
		return s.isEmpty() ? "generated-skill" : s;
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
