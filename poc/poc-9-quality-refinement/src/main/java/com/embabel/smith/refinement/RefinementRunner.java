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

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.embabel.agent.api.common.PlannerType;
import com.embabel.agent.core.Agent;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.AgentScope;
import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.core.DomainType;
import com.embabel.agent.core.DynamicType;
import com.embabel.agent.core.JvmType;
import com.embabel.agent.core.ProcessOptions;
import com.embabel.agent.core.PropertyDefinition;
import com.embabel.agent.core.ValuePropertyDefinition;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.eval.support.FullScores;
import com.embabel.agent.eval.support.Score;
import com.embabel.agent.spec.StepSpecAgentScopeBuilder;
import com.embabel.agent.spec.model.BackgroundMessage;
import com.embabel.agent.spec.yml.YmlStepSpecRepository;
import com.embabel.smith.common.io.AgentSpecWriter;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.DynamicTypeDef;
import com.embabel.smith.common.model.PropertyDef;
import com.embabel.smith.common.validation.AgentSpecValidator;
import com.embabel.smith.common.validation.ValidationResult;

/**
 * The eval-guided refinement loop (POC 9): evaluate a weak spec, rewrite its prompt from
 * the failing tasks, re-evaluate, and report the before/after composite score.
 * Endpoint-gated (the spec runs and the rewrite both call the model); the scoring and
 * re-validation halves are pure.
 *
 * @author Ivan Rodriguez
 */
public class RefinementRunner {

	private static final CopyOption[] COPY = { StandardCopyOption.REPLACE_EXISTING };

	private final AgentPlatform platform;

	private final RefinementScorer scorer = new RefinementScorer();

	private final PromptRewriter rewriter = new PromptRewriter();

	private final AgentSpecWriter writer = new AgentSpecWriter();

	private final AgentSpecValidator validator = new AgentSpecValidator();

	public RefinementRunner(AgentPlatform platform) {
		this.platform = platform;
	}

	public RefinementOutcome improve(AgentSpec pack, String stepName, List<SpecEvalTask> tasks, Endpoint endpoint) {
		Agent before = deploy(pack, "refine-before");
		FullScores beforeScores = evaluate(before, tasks);
		List<SpecEvalTask> failing = failingTasks(beforeScores, tasks);
		if (failing.isEmpty()) {
			return new RefinementOutcome(beforeScores.getTotalScore(), beforeScores.getTotalScore(), null, List.of());
		}

		String revisedPrompt = this.rewriter.rewrite(endpoint.baseUrl(), endpoint.apiKey(), endpoint.model(),
				pack.step(stepName).prompt(), failing);
		PromptRewriter.RewriteResult applied = this.rewriter.applyRevisedPrompt(pack, stepName, revisedPrompt);
		if (!applied.valid()) {
			throw new IllegalStateException("Revised prompt failed validation: " + applied.issues());
		}

		Agent after = deploy(applied.revisedPack(), "refine-after");
		FullScores afterScores = evaluate(after, tasks);

		return new RefinementOutcome(beforeScores.getTotalScore(), afterScores.getTotalScore(), revisedPrompt,
				failing.stream().map(SpecEvalTask::input).toList());
	}

	private FullScores evaluate(Agent agent, List<SpecEvalTask> tasks) {
		List<Score> scores = tasks.stream()
			.map((task) -> this.scorer.scoreOne(invoke(agent, task.input()), task))
			.toList();
		return this.scorer.composite(scores);
	}

	private String invoke(Agent agent, String input) {
		AgentProcess process = this.platform.runAgentFrom(agent, ProcessOptions.DEFAULT.withPlannerType(PlannerType.GOAP),
				Map.of("userInput", new UserInput(input)));
		Object last = process.lastResult();
		return (last instanceof BackgroundMessage message) ? message.getContent() : null;
	}

	private List<SpecEvalTask> failingTasks(FullScores scores, List<SpecEvalTask> tasks) {
		return scores.getAssertionScores()
			.stream()
			.filter((s) -> s.getScore() < 1.0)
			.map(Score::getScored)
			.filter((input) -> tasks.stream().anyMatch((t) -> t.input().equals(input)))
			.map((input) -> tasks.stream().filter((t) -> t.input().equals(input)).findFirst().orElseThrow())
			.toList();
	}

	private Agent deploy(AgentSpec pack, String agentName) {
		ValidationResult result = this.validator.validate(pack);
		if (!result.isValid()) {
			throw new IllegalStateException("Spec-pack failed validation: " + result.issues());
		}
		try {
			DataDictionary types = this.platform
				.plus(DataDictionary.fromDomainTypes(agentName + "-types", runtimeTypes(pack)));
			Path stepsDir = assembleStepSpecs(pack);
			StepSpecAgentScopeBuilder builder = new StepSpecAgentScopeBuilder(agentName, this.platform,
					new YmlStepSpecRepository(stepsDir.toString(), List.of()), types, List.of(), List.of());
			AgentScope scope = builder.deploy();
			Agent agent = scope.createAgent(agentName, "spec", "refinement target: " + pack.name());
			this.platform.deploy(agent);
			return agent;
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to deploy " + pack.name() + ": " + ex.getMessage(), ex);
		}
	}

	private List<DomainType> runtimeTypes(AgentSpec pack) {
		List<DomainType> types = new ArrayList<>();
		types.add(new JvmType(UserInput.class));
		types.add(new JvmType(BackgroundMessage.class));
		for (DynamicTypeDef def : pack.types()) {
			List<PropertyDefinition> properties = new ArrayList<>();
			for (PropertyDef property : def.ownProperties()) {
				properties.add(new ValuePropertyDefinition(property.name(), property.type()));
			}
			types.add(new DynamicType(def.name(), def.description(), properties, List.of(), def.isCreationPermitted()));
		}
		return types;
	}

	private Path assembleStepSpecs(AgentSpec pack) throws IOException {
		Path packDir = this.writer.write(pack, Files.createTempDirectory("refine-pack-"));
		Path target = Files.createTempDirectory("refine-steps-");
		Files.copy(packDir.resolve("goal.yml"), target.resolve("goal.yml"), COPY);
		Path stepsDir = packDir.resolve("steps");
		if (Files.isDirectory(stepsDir)) {
			try (var files = Files.list(stepsDir)) {
				for (Path step : files.filter(Files::isRegularFile).toList()) {
					Files.copy(step, target.resolve(step.getFileName().toString()), COPY);
				}
			}
		}
		return target;
	}

	/**
	 * The model endpoint for the rewrite call (env-driven, matching the rest of the POC).
	 *
	 * @param baseUrl the OpenAI-compatible endpoint base URL
	 * @param apiKey the API key for the endpoint
	 * @param model the model name to call
	 */
	public record Endpoint(String baseUrl, String apiKey, String model) {
	}

	/**
	 * The outcome of a refinement pass: before/after scores, the revised prompt, and what
	 * failed.
	 *
	 * @param beforeScore the composite score before the rewrite
	 * @param afterScore the composite score after the rewrite
	 * @param revisedPrompt the revised prompt, or null when nothing failed
	 * @param failingInputs the eval inputs that failed before the rewrite
	 */
	public record RefinementOutcome(double beforeScore, double afterScore, String revisedPrompt,
			List<String> failingInputs) {
		public RefinementOutcome {
			failingInputs = (failingInputs != null) ? List.copyOf(failingInputs) : List.of();
		}

		public boolean improved() {
			return this.afterScore > this.beforeScore;
		}
	}

}
