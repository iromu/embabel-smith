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

package com.embabel.smith.lifecycle;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

import com.embabel.agent.api.common.PlannerType;
import com.embabel.agent.core.Agent;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.AgentScope;
import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.core.JvmType;
import com.embabel.agent.core.ProcessOptions;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.spec.StepSpecAgentScopeBuilder;
import com.embabel.agent.spec.model.BackgroundMessage;
import com.embabel.agent.spec.yml.YmlStepSpecRepository;
import com.embabel.smith.common.io.AgentSpecWriter;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.validation.AgentSpecValidator;
import com.embabel.smith.common.validation.ValidationResult;

/**
 * The endpoint-gated half of POC 18: deploy a spec and measure its live eval pass-rate.
 * Deploys the spec as a throwaway agent (same wiring as POC 9's {@code RefinementRunner})
 * and scores the goal output of each eval task against its deterministic acceptance
 * check.
 *
 * @author Ivan Rodriguez
 */
public final class LiveEvalRunner {

	private static final CopyOption[] COPY = { StandardCopyOption.REPLACE_EXISTING };

	private final AgentPlatform platform;

	private final AgentSpecWriter writer = new AgentSpecWriter();

	private final AgentSpecValidator validator = new AgentSpecValidator();

	public LiveEvalRunner(AgentPlatform platform) {
		this.platform = platform;
	}

	/**
	 * The live eval pass-rate (0.0–1.0) of {@code pack} across {@code tasks}.
	 * @param pack the spec to deploy and evaluate.
	 * @param tasks the eval tasks to score the goal output against.
	 * @return the pass-rate, from 0.0 to 1.0.
	 */
	public double evaluate(AgentSpec pack, List<SpecEvalTask> tasks) {
		Agent agent = deploy(pack, "lifecycle-" + pack.name());
		double sum = 0.0;
		for (SpecEvalTask task : tasks) {
			String output = invoke(agent, task.input());
			sum += scoreOne(output, task);
		}
		return sum / tasks.size();
	}

	private double scoreOne(String goalOutput, SpecEvalTask task) {
		return (goalOutput != null && !goalOutput.isBlank() && task.acceptance().test(goalOutput)) ? 1.0 : 0.0;
	}

	private String invoke(Agent agent, String input) {
		AgentProcess process = this.platform.runAgentFrom(agent,
				ProcessOptions.DEFAULT.withPlannerType(PlannerType.GOAP),
				Map.of("userInput", new UserInput(input)));
		Object last = process.lastResult();
		return (last instanceof BackgroundMessage message) ? message.getContent() : null;
	}

	private Agent deploy(AgentSpec pack, String agentName) {
		ValidationResult result = this.validator.validate(pack);
		if (!result.isValid()) {
			throw new IllegalStateException("Spec-pack failed validation: " + result.issues());
		}
		try {
			DataDictionary types = this.platform.plus(DataDictionary.fromDomainTypes(agentName + "-types",
					List.of(new JvmType(UserInput.class), new JvmType(BackgroundMessage.class))));
			Path stepsDir = assembleStepSpecs(pack);
			StepSpecAgentScopeBuilder builder = new StepSpecAgentScopeBuilder(agentName, this.platform,
					new YmlStepSpecRepository(stepsDir.toString(), List.of()), types, List.of(), List.of());
			AgentScope scope = builder.deploy();
			Agent agent = scope.createAgent(agentName, "spec", "lifecycle target: " + pack.name());
			this.platform.deploy(agent);
			return agent;
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to deploy " + pack.name() + ": " + ex.getMessage(), ex);
		}
	}

	private Path assembleStepSpecs(AgentSpec pack) throws IOException {
		Path packDir = this.writer.write(pack, Files.createTempDirectory("lifecycle-pack-"));
		Path target = Files.createTempDirectory("lifecycle-steps-");
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

}
