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

package com.embabel.smith.agentspecformat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.embabel.agent.api.common.PlannerType;
import com.embabel.agent.core.Agent;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.AgentScope;
import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.core.ProcessOptions;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.spec.StepSpecAgentScopeBuilder;
import com.embabel.agent.spec.model.BackgroundMessage;
import com.embabel.agent.spec.yml.YmlStepSpecRepository;
import com.embabel.smith.common.config.ModelEndpointConfig;
import com.embabel.smith.common.io.AgentSpecReader;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.validation.AgentSpecValidator;
import com.embabel.smith.common.validation.ValidationResult;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * POC 4 launcher: loads a single exported goal on demand and plans over its action chain.
 *
 * <p>
 * Confirms the exported-goal-as-spec primitive: an exported goal is a callable spec whose
 * chain can be loaded and planned on demand, while a goal declared {@code export: false}
 * is not exposed as a callable spec.
 *
 * @author Ivan Rodriguez
 */
@SpringBootApplication
public class SpecFormatApp implements CommandLineRunner {

	private final AgentPlatform platform;

	private final ModelEndpointConfig endpoint;

	private final Path packDir;

	private final String input;

	private final SpecSurface surface = new SpecSurface();

	public SpecFormatApp(AgentPlatform platform, @Value("${OPENAI_CUSTOM_BASE_URL:}") String baseUrl,
			@Value("${OPENAI_CUSTOM_API_KEY:}") String apiKey, @Value("${OPENAI_CUSTOM_MODELS:}") String models,
			@Value("${EMBABEL_MODELS_DEFAULT_LLM:}") String defaultLlm,
			@Value("${builder.pack-dir:agent-specs/explainer}") Path packDir,
			@Value("${builder.input:}") String input) {
		this.platform = platform;
		this.endpoint = new ModelEndpointConfig(baseUrl, apiKey, models, defaultLlm);
		this.packDir = packDir;
		this.input = input;
	}

	public static void main(String[] args) {
		SpringApplication.run(SpecFormatApp.class, args);
	}

	@Override
	public void run(String... args) {
		String configError = this.endpoint.configError();
		if (configError != null) {
			throw new IllegalStateException(configError);
		}

		AgentSpec pack = readAndValidate(this.packDir);
		GoalStepSpec spec = this.surface.callableSpec(pack);
		if (spec == null) {
			System.out.println("No callable spec: the goal is not exported (export: false). Nothing is exposed.");
			return;
		}

		List<com.embabel.smith.common.model.ActionStepSpec> chain = this.surface.actionChain(pack);
		System.out.println("Loaded spec '" + spec.name() + "' on demand; action chain: " + chain.size() + " step(s).");

		DataDictionary types = this.platform.plus(DataDictionary.fromDomainTypes("spec-types", runtimeTypes(pack)));
		Path stepsDir = assembleOnDemand(this.packDir, chain);
		StepSpecAgentScopeBuilder builder = new StepSpecAgentScopeBuilder("spec-format-agent", this.platform,
				new YmlStepSpecRepository(stepsDir.toString(), List.of()), types, List.of(), List.of());
		AgentScope scope = builder.deploy();
		Agent agent = scope.createAgent("spec-format-agent", "spec", "On-demand spec: " + spec.name());
		this.platform.deploy(agent);

		AgentProcess process = this.platform.runAgentFrom(agent, ProcessOptions.DEFAULT.withPlannerType(PlannerType.GOAP),
				Map.of("userInput", new UserInput(this.input)));

		Object last = process.lastResult();
		if (last instanceof BackgroundMessage message) {
			System.out.println("Plan resolved to the goal output type; result: " + message.getContent());
		}
		else {
			throw new IllegalStateException(
					"Plan did not resolve to the goal output type (status=" + process.getStatus() + ")");
		}
	}

	private AgentSpec readAndValidate(Path dir) {
		try {
			AgentSpec pack = new AgentSpecReader().read(dir);
			ValidationResult result = new AgentSpecValidator().validate(dir);
			if (!result.isValid()) {
				throw new IllegalStateException("Spec-pack failed validation: " + result.issues());
			}
			return pack;
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to read agent spec: " + ex.getMessage(), ex);
		}
	}

	private List<com.embabel.agent.core.DomainType> runtimeTypes(AgentSpec pack) {
		List<com.embabel.agent.core.DomainType> types = new java.util.ArrayList<>();
		types.add(new com.embabel.agent.core.JvmType(UserInput.class));
		types.add(new com.embabel.agent.core.JvmType(BackgroundMessage.class));
		for (var def : pack.types()) {
			types.add(new com.embabel.agent.core.DynamicType(def.name(), def.description(),
					def.ownProperties()
						.stream()
						.map((p) -> new com.embabel.agent.core.ValuePropertyDefinition(p.name(), p.type()))
						.toList(),
					List.of(), def.isCreationPermitted()));
		}
		return types;
	}

	private Path assembleOnDemand(Path packDir, List<com.embabel.smith.common.model.ActionStepSpec> chain) {
		try {
			Path target = Files.createTempDirectory("spec-on-demand-");
			new OnDemandAssembler().assemble(packDir, target, chain);
			return target;
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to assemble on-demand steps: " + ex.getMessage(), ex);
		}
	}

}
