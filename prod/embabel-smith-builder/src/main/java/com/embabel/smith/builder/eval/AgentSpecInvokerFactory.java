/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
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
import com.embabel.agent.core.Goal;
import com.embabel.agent.core.ProcessOptions;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.spec.StepSpecAgentScopeBuilder;
import com.embabel.agent.spec.model.BackgroundMessage;
import com.embabel.agent.spec.yml.YmlStepSpecRepository;
import com.embabel.smith.common.io.AgentSpecWriter;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.ProcessSpec;

/**
 * The production {@link InvokerFactory} and {@link ProcessInvokerFactory}: deploys the
 * pack — or every pack of a process — and returns an invoker that runs the <em>entry</em>
 * goal to completion, the same {@code runAgentFrom} path the product runtime uses, so a
 * candidate is evaluated whatever type its goal produces. Endpoint-gated — each call
 * deploys and the invocation calls the model.
 *
 * <p>
 * A process is deployed as one scope rather than one agent per pack: the planner has to
 * see every pack's steps and goals together, because the hand-off between two agents is a
 * shared type and a planner that can only see one pack at a time cannot plan across it.
 *
 * @author Ivan Rodriguez
 */
public class AgentSpecInvokerFactory implements InvokerFactory, ProcessInvokerFactory {

	private final AgentPlatform platform;

	private final AgentSpecTypeMapper typeMapper = new AgentSpecTypeMapper();

	private final StepSpecAssembler assembler = new StepSpecAssembler();

	private final AgentSpecWriter writer = new AgentSpecWriter();

	public AgentSpecInvokerFactory(AgentPlatform platform) {
		this.platform = platform;
	}

	@Override
	public AgentSpecInvoker invokerFor(AgentSpec pack) {
		// A lone pack exports one goal, so the deployed agent already runs the right one.
		return invokerFor(List.of(pack), null, null);
	}

	@Override
	public AgentSpecInvoker invokerFor(AgentSpec pack, String model) {
		return invokerFor(List.of(pack), null, model);
	}

	@Override
	public AgentSpecInvoker invokerFor(ProcessSpec process) {
		return invokerFor(process.agents(), entryGoalName(process), null);
	}

	@Override
	public AgentSpecInvoker invokerFor(ProcessSpec process, String model) {
		return invokerFor(process.agents(), entryGoalName(process), model);
	}

	private AgentSpecInvoker invokerFor(List<AgentSpec> packs, String goalName, String model) {
		List<Path> packDirs = packs.stream().map(this::writeTempPack).toList();
		DataDictionary types = this.platform
			.plus(DataDictionary.fromDomainTypes("builder-eval-types", this.typeMapper.allRuntimeTypes(packs)));
		Path stepsDir;
		try {
			stepsDir = this.assembler.assemble(packDirs, Files.createTempDirectory("builder-eval-steps-"), model);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to assemble step specs for eval: " + ex.getMessage(), ex);
		}
		String suffix = Long.toHexString(System.nanoTime());
		StepSpecAgentScopeBuilder builder = new StepSpecAgentScopeBuilder("builder-eval-scope-" + suffix, this.platform,
				new YmlStepSpecRepository(stepsDir.toString(), List.of()), types, List.of(), List.of());
		Agent agent = builder.deploy()
			.createAgent("builder-eval-agent-" + suffix, "eval-spec", "Builder eval: " + packs.get(0).name());
		Agent goalAgent = (null != goalName) ? withGoal(agent, goalName) : agent;
		this.platform.deploy(goalAgent);
		return (input) -> {
			// Run the entry goal, whatever its output type is — a spec's goal output is
			// the
			// deliverable it was generated to produce (a domain type), so looking the
			// agent
			// up by a fixed output class would never find it.
			AgentProcess process = this.platform.runAgentFrom(goalAgent, ProcessOptions.DEFAULT,
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

	/**
	 * Narrows the deployed agent to the goal the run has to deliver. A process deploys
	 * every agent's goal, so without this the planner would be free to stop at the first
	 * one.
	 * @param agent the agent built from the whole scope
	 * @param goalName the entry goal to run
	 * @return the agent narrowed to that goal
	 * @throws IllegalStateException when no pack exports a goal of that name
	 */
	private static Agent withGoal(Agent agent, String goalName) {
		Goal goal = agent.getGoals()
			.stream()
			.filter((g) -> goalName.equals(g.getName()))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("No pack of the candidate exports the goal '" + goalName
					+ "'; goals are " + agent.getGoals().stream().map(Goal::getName).toList()));
		return agent.withSingleGoal(goal);
	}

	/**
	 * The goal the process must deliver — its last agent's, which is the process's final
	 * deliverable rather than any intermediate hand-off.
	 * @param process the process about to be deployed
	 * @return the name of the goal to run, or {@code null} when the process has no agents
	 */
	private static String entryGoalName(ProcessSpec process) {
		if (process.agents().isEmpty()) {
			return null;
		}
		AgentSpec last = process.agents().get(process.agents().size() - 1);
		return (last.goal() != null) ? last.goal().name() : null;
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
