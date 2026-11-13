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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;

/**
 * The soundness check for a {@link ProcessSpec}: per-agent (each agent's goal is reachable from
 * its own steps) and cross-agent (every step input is fed process-wide, goal names are unique,
 * and the process flows from {@code UserInput} to the final goal). Returns the issues plus the
 * shared-type count and connectivity flag that the comparison reports on.
 *
 * @author Ivan Rodriguez
 */
public final class ProcessValidator {

	private static final String USER_INPUT = "UserInput";

	private ProcessValidator() {
	}

	public static ValidationResult validate(ProcessSpec process) {
		List<String> issues = new ArrayList<>();
		List<AgentSpec> agents = process.agents();

		if (agents.isEmpty()) {
			issues.add("The process has no agents.");
			return new ValidationResult(false, issues, 0, false);
		}

		issues.addAll(goalNameCollisions(agents));
		int shared = sharedTypeCount(agents);

		// Forward reachability from UserInput across the agents, in run order.
		Set<String> available = new HashSet<>();
		available.add(USER_INPUT);
		boolean connected = false;
		for (int i = 0; i < agents.size(); i++) {
			AgentSpec agent = agents.get(i);
			String label = "agent '" + agent.name() + "'";
			if (agent.goal() == null || !agent.goal().isExported()) {
				issues.add(label + " has no exported goal.");
			}
			if (agent.steps().isEmpty()) {
				issues.add(label + " has no action steps.");
				continue;
			}
			String goalOutput = agent.goal().outputTypeName();
			// Structural: the goal's output type must be produced by one of the agent's own steps.
			boolean goalProduced = agent.steps()
				.stream()
				.anyMatch((s) -> goalOutput != null && goalOutput.equals(s.outputTypeName()));
			if (!goalProduced) {
				issues.add(label + " goal output '" + goalOutput + "' is not produced by any of its own steps.");
			}
			// Connectivity: the agent must be fed from the process (UserInput for the first, an
			// earlier agent's output otherwise) and its goal must be reachable from that entry.
			boolean entered = (i == 0) || consumesAvailable(agent, available);
			if (!entered) {
				issues.add(label + " is not connected to the process (no earlier agent feeds it).");
			}
			Set<String> local = new HashSet<>(available);
			boolean changed = true;
			while (changed) {
				changed = false;
				for (ActionStepSpec step : agent.steps()) {
					if (step.outputTypeName() != null && !local.contains(step.outputTypeName())
							&& inputsSatisfied(step, local)) {
						local.add(step.outputTypeName());
						changed = true;
					}
				}
			}
			if (goalOutput != null && local.contains(goalOutput)) {
				available.add(goalOutput);
				connected = (i == agents.size() - 1);
			}
			else if (goalOutput != null && entered) {
				issues.add(label + " goal output '" + goalOutput + "' is not reachable from the process entry.");
			}
		}

		// Process-wide: every step input must be produced by some step or goal, or be UserInput.
		Set<String> produced = new HashSet<>();
		produced.add(USER_INPUT);
		for (AgentSpec agent : agents) {
			for (ActionStepSpec step : agent.steps()) {
				if (step.outputTypeName() != null) {
					produced.add(step.outputTypeName());
				}
			}
			if (agent.goal() != null && agent.goal().outputTypeName() != null) {
				produced.add(agent.goal().outputTypeName());
			}
		}
		for (AgentSpec agent : agents) {
			for (ActionStepSpec step : agent.steps()) {
				for (String input : step.inputTypeNames()) {
					if (!produced.contains(input)) {
						issues.add("agent '" + agent.name() + "' step '" + step.name() + "' needs input '" + input
								+ "', which no agent produces.");
					}
				}
			}
		}

		return new ValidationResult(issues.isEmpty(), issues, shared, connected);
	}

	/**
	 * Two or more agents exporting the same goal name — the runtime rejects this.
	 * @param agents the agents to check.
	 * @return the collision issues, empty when there are none.
	 */
	private static List<String> goalNameCollisions(List<AgentSpec> agents) {
		Map<String, List<String>> goalToAgents = new HashMap<>();
		for (AgentSpec agent : agents) {
			if (agent.goal() != null && agent.goal().name() != null) {
				goalToAgents.computeIfAbsent(agent.goal().name(), (k) -> new ArrayList<>()).add(agent.name());
			}
		}
		List<String> issues = new ArrayList<>();
		for (Map.Entry<String, List<String>> entry : goalToAgents.entrySet()) {
			if (entry.getValue().size() > 1) {
				issues.add("Goal name '" + entry.getKey() + "' is exported by more than one agent: "
						+ entry.getValue());
			}
		}
		return issues;
	}

	/**
	 * The number of distinct types referenced by more than one agent — the cross-agent glue.
	 * @param agents the agents to inspect.
	 * @return the number of shared (cross-agent) types.
	 */
	private static int sharedTypeCount(List<AgentSpec> agents) {
		Map<String, Set<String>> typeToAgents = new HashMap<>();
		for (AgentSpec agent : agents) {
			Set<String> refs = new LinkedHashSet<>();
			for (ActionStepSpec step : agent.steps()) {
				refs.addAll(step.inputTypeNames());
				if (step.outputTypeName() != null) {
					refs.add(step.outputTypeName());
				}
			}
			if (agent.goal() != null && agent.goal().outputTypeName() != null) {
				refs.add(agent.goal().outputTypeName());
			}
			for (String type : refs) {
				if (!USER_INPUT.equals(type)) {
					typeToAgents.computeIfAbsent(type, (k) -> new HashSet<>()).add(agent.name());
				}
			}
		}
		int shared = 0;
		for (Set<String> agentSet : typeToAgents.values()) {
			if (agentSet.size() > 1) {
				shared++;
			}
		}
		return shared;
	}

	private static boolean consumesAvailable(AgentSpec agent, Set<String> available) {
		for (ActionStepSpec step : agent.steps()) {
			if (step.inputTypeNames().stream().anyMatch(available::contains)) {
				return true;
			}
		}
		return false;
	}

	private static boolean inputsSatisfied(ActionStepSpec step, Set<String> available) {
		if (step.inputTypeNames().isEmpty()) {
			return false;
		}
		return step.inputTypeNames().stream().allMatch(available::contains);
	}

	/**
	 * The validation outcome: validity, issues, shared-type count, and process connectivity.
	 * @param valid whether the process is sound (no issues).
	 * @param issues the list of issues, empty when valid.
	 * @param sharedTypes the number of types referenced by more than one agent.
	 * @param connected whether the process flows from UserInput to the final goal.
	 */
	public record ValidationResult(boolean valid, List<String> issues, int sharedTypes, boolean connected) {
	}

}
