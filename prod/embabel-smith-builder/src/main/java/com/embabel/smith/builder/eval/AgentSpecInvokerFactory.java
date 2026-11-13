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

package com.embabel.smith.builder.eval;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.embabel.agent.core.Agent;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.AgentProcessStatusCode;
import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.core.ProcessOptions;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.spec.StepSpecAgentScopeBuilder;
import com.embabel.agent.spec.model.BackgroundMessage;
import com.embabel.agent.spec.yml.YmlStepSpecRepository;
import com.embabel.smith.common.io.AgentSpecWriter;
import com.embabel.smith.common.model.AgentSpec;

/**
 * The production {@link InvokerFactory}: deploys the pack just built and returns an
 * invoker that runs the pack's <em>own</em> goal to completion — the same
 * {@code runAgentFrom} path the product runtime uses — so a spec is evaluated whatever
 * type its goal produces. Endpoint-gated — each call deploys the pack and the invocation
 * calls the model.
 *
 * @author Ivan Rodriguez
 */
public class AgentSpecInvokerFactory implements InvokerFactory {

	private final AgentPlatform platform;

	private final AgentSpecTypeMapper typeMapper = new AgentSpecTypeMapper();

	private final StepSpecAssembler assembler = new StepSpecAssembler();

	private final AgentSpecWriter writer = new AgentSpecWriter();

	public AgentSpecInvokerFactory(AgentPlatform platform) {
		this.platform = platform;
	}

	@Override
	public AgentSpecInvoker invokerFor(AgentSpec pack) {
		Path packDir = writeTempPack(pack);
		DataDictionary types = this.platform
			.plus(DataDictionary.fromDomainTypes("builder-eval-types", this.typeMapper.allRuntimeTypes(pack)));
		Path stepsDir;
		try {
			stepsDir = this.assembler.assemble(List.of(packDir), Files.createTempDirectory("builder-eval-steps-"));
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to assemble step specs for eval: " + ex.getMessage(), ex);
		}
		String suffix = Long.toHexString(System.nanoTime());
		StepSpecAgentScopeBuilder builder = new StepSpecAgentScopeBuilder("builder-eval-scope-" + suffix,
				this.platform, new YmlStepSpecRepository(stepsDir.toString(), List.of()), types, List.of(),
				List.of());
		Agent agent = builder.deploy()
			.createAgent("builder-eval-agent-" + suffix, "eval-spec", "Builder eval: " + pack.name());
		this.platform.deploy(agent);
		return (input) -> {
			// Run the pack's own goal, whatever its output type is — a spec's goal output
			// is the deliverable it was generated to produce (a domain type), so looking
			// the agent up by a fixed output class would never find it.
			AgentProcess process = this.platform.runAgentFrom(agent, ProcessOptions.DEFAULT,
					Map.of("userInput", new UserInput(input)));
			if (process.getStatus() != AgentProcessStatusCode.COMPLETED) {
				// The goal was not achieved: no output, so the eval task fails and the
				// refinement loop gets the chance to fix it.
				return null;
			}
			Object last = process.lastResult();
			if (last == null) {
				return null;
			}
			return (last instanceof BackgroundMessage message) ? message.getContent() : String.valueOf(last);
		};
	}

	private Path writeTempPack(AgentSpec pack) {
		try {
			Path dir = Files.createTempDirectory("builder-eval-pack-");
			this.writer.write(pack, dir);
			return dir;
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to write pack for eval: " + ex.getMessage(), ex);
		}
	}

}
