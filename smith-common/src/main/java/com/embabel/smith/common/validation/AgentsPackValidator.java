/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.common.validation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentsPack;

/**
 * The cross-agent soundness check for an {@link AgentsPack} — the gate a single pack's
 * {@link AgentSpecValidator} cannot reach, because it is about how the agents hand off to
 * each other rather than about any one of them.
 *
 * <p>
 * Checks, per agent and pack-wide:
 * <ol>
 * <li><b>Exported goal</b> — every agent exports a goal and has steps.</li>
 * <li><b>Goal produced</b> — an agent's goal output is produced by one of its own
 * steps.</li>
 * <li><b>Connected</b> — every agent is fed by an earlier agent (or, for the first, by
 * {@code UserInput}), and the final goal is reachable from that entry.</li>
 * <li><b>Fed</b> — every step input is produced by some agent in the pack, or is
 * {@code UserInput}.</li>
 * <li><b>Unique goals</b> — no two agents export the same goal name; the runtime rejects
 * a world where two do.</li>
 * <li><b>Cycle</b> — no agent depends on the output of an agent that depends on its
 * own.</li>
 * </ol>
 *
 * <p>
 * Type names are compared by their short name, so a step that declares
 * {@code com.embabel.agent.domain.io.UserInput} and one that declares {@code UserInput}
 * are the same hand-off.
 *
 * @author Ivan Rodriguez
 */
public class AgentsPackValidator {

	private static final String USER_INPUT = "UserInput";

	/**
	 * Validates the cross-agent hand-offs of an agents-pack.
	 * @param agentsPack the agents-pack to validate
	 * @return the findings, the shared-type count, the connectivity flag and any cycle
	 */
	public AgentsPackValidationResult validate(AgentsPack agentsPack) {
		List<AgentSpec> agents = agentsPack.agents();
		if (agents.isEmpty()) {
			return new AgentsPackValidationResult(List.of("The agents-pack has no agents."), 0, false, List.of());
		}

		List<String> issues = new ArrayList<>(goalNameCollisions(agents));
		Flow flow = checkFlow(agents);
		issues.addAll(flow.issues);
		issues.addAll(unfedInputs(agents));

		List<String> cycle = findCycle(agents);
		if (!cycle.isEmpty()) {
			issues.add("Circular dependency between agents: " + String.join(" -> ", cycle) + " -> " + cycle.get(0));
		}
		return new AgentsPackValidationResult(issues, sharedTypeCount(agents), flow.connected, cycle);
	}

	/**
	 * The per-agent checks and the forward reachability of the final goal, walked in run
	 * order from {@code UserInput}.
	 * @param agents the agents of the agents-pack, in run order
	 * @return the issues found and whether the final goal was reached
	 */
	private static Flow checkFlow(List<AgentSpec> agents) {
		List<String> issues = new ArrayList<>();
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
			String goalOutput = shortName(agent.goal().outputTypeName());
			if (goalOutput == null
					|| agent.steps().stream().noneMatch((s) -> goalOutput.equals(shortName(s.outputTypeName())))) {
				issues.add(label + " goal output '" + agent.goal().outputTypeName()
						+ "' is not produced by any of its own steps.");
			}

			boolean entered = (i == 0) || consumesAvailable(agent, available);
			if (!entered) {
				issues.add(label + " is not connected to the agents-pack (no earlier agent feeds it).");
			}

			Set<String> local = new HashSet<>(available);
			boolean changed = true;
			while (changed) {
				changed = false;
				for (ActionStepSpec step : agent.steps()) {
					String output = shortName(step.outputTypeName());
					if (output != null && !local.contains(output) && inputsSatisfied(step, local)) {
						local.add(output);
						changed = true;
					}
				}
			}
			if (goalOutput != null && local.contains(goalOutput)) {
				available.add(goalOutput);
				connected = (i == agents.size() - 1);
			}
			else if (goalOutput != null && entered) {
				issues.add(label + " goal output '" + agent.goal().outputTypeName()
						+ "' is not reachable from the agents-pack entry.");
			}
		}
		return new Flow(issues, connected);
	}

	/**
	 * Two or more agents exporting the same goal name — the runtime rejects this.
	 * @param agents the agents to check
	 * @return the collision issues, empty when there are none
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
				issues
					.add("Goal name '" + entry.getKey() + "' is exported by more than one agent: " + entry.getValue());
			}
		}
		return issues;
	}

	/**
	 * Step inputs no agent in the pack produces — a hand-off the model named but never
	 * wired.
	 * @param agents the agents to check
	 * @return the issues, empty when every input is fed
	 */
	private static List<String> unfedInputs(List<AgentSpec> agents) {
		Set<String> produced = new HashSet<>();
		produced.add(USER_INPUT);
		for (AgentSpec agent : agents) {
			for (ActionStepSpec step : agent.steps()) {
				addNonNull(produced, shortName(step.outputTypeName()));
			}
			if (agent.goal() != null) {
				addNonNull(produced, shortName(agent.goal().outputTypeName()));
			}
		}
		List<String> issues = new ArrayList<>();
		for (AgentSpec agent : agents) {
			for (ActionStepSpec step : agent.steps()) {
				for (String input : step.inputTypeNames()) {
					if (!produced.contains(shortName(input))) {
						issues.add("agent '" + agent.name() + "' step '" + step.name() + "' needs input '" + input
								+ "', which no agent produces.");
					}
				}
			}
		}
		return issues;
	}

	/**
	 * The number of distinct types referenced by more than one agent — the cross-agent
	 * glue that distinguishes a decomposed agents-pack from a set of unrelated packs.
	 * @param agents the agents to inspect
	 * @return the number of shared (cross-agent) types
	 */
	private static int sharedTypeCount(List<AgentSpec> agents) {
		Map<String, Set<String>> typeToAgents = new HashMap<>();
		for (AgentSpec agent : agents) {
			for (String type : referencedTypes(agent)) {
				typeToAgents.computeIfAbsent(type, (k) -> new HashSet<>()).add(agent.name());
			}
		}
		int shared = 0;
		for (Set<String> owners : typeToAgents.values()) {
			if (owners.size() > 1) {
				shared++;
			}
		}
		return shared;
	}

	/**
	 * The circular dependency in the agents-pack, if any.
	 *
	 * <p>
	 * The graph has a data edge from the agent that produces a type to the agent that
	 * consumes it, and a sequence edge from each agent to the one the agents-pack
	 * declares after it. A cycle means the declared order and the data flow contradict
	 * each other — an agent is asked to run before the agent whose output it feeds — or
	 * that two agents genuinely depend on each other's outputs.
	 * @param agents the agents of the agents-pack, in run order
	 * @return the agents of the first cycle found, in dependency order; empty when
	 * acyclic
	 */
	private static List<String> findCycle(List<AgentSpec> agents) {
		List<List<Integer>> edges = dependencyEdges(agents, true);
		return firstCycle(agents, edges);
	}

	/**
	 * The agent-level dependency graph: which agent must run before which.
	 * @param agents the agents of the agents-pack, in run order
	 * @param withSequence whether to include the declared run-order edges; without them
	 * the graph is the pure data flow, which is what a repair reorders against
	 * @return the adjacency list, indexed by agent position
	 */
	public static List<List<Integer>> dependencyEdges(List<AgentSpec> agents, boolean withSequence) {
		int n = agents.size();
		List<List<Integer>> edges = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			edges.add(new ArrayList<>());
		}
		if (withSequence) {
			for (int i = 0; i + 1 < n; i++) {
				edges.get(i).add(i + 1);
			}
		}
		Map<String, Integer> producerOf = new LinkedHashMap<>();
		for (int i = 0; i < n; i++) {
			for (String type : producedTypes(agents.get(i))) {
				producerOf.putIfAbsent(type, i);
			}
		}
		for (int consumer = 0; consumer < n; consumer++) {
			for (String type : consumedTypes(agents.get(consumer))) {
				Integer producer = producerOf.get(type);
				if (producer != null && producer != consumer && !edges.get(producer).contains(consumer)) {
					edges.get(producer).add(consumer);
				}
			}
		}
		return edges;
	}

	/**
	 * The first cycle reachable in an adjacency list, found by a depth-first walk.
	 * @param agents the agents the graph is over, for the names it reports
	 * @param edges the adjacency list
	 * @return the agents of the cycle, in dependency order; empty when acyclic
	 */
	static List<String> firstCycle(List<AgentSpec> agents, List<List<Integer>> edges) {
		int[] state = new int[agents.size()];
		List<Integer> path = new ArrayList<>();
		for (int start = 0; start < agents.size(); start++) {
			List<String> cycle = walk(agents, edges, state, path, start);
			if (!cycle.isEmpty()) {
				return cycle;
			}
		}
		return List.of();
	}

	private static List<String> walk(List<AgentSpec> agents, List<List<Integer>> edges, int[] state, List<Integer> path,
			int node) {
		if (state[node] == 2) {
			return List.of();
		}
		if (state[node] == 1) {
			List<String> cycle = new ArrayList<>();
			for (int i = path.indexOf(node); i < path.size(); i++) {
				cycle.add(agents.get(path.get(i)).name());
			}
			return cycle;
		}
		state[node] = 1;
		path.add(node);
		for (int next : edges.get(node)) {
			List<String> cycle = walk(agents, edges, state, path, next);
			if (!cycle.isEmpty()) {
				return cycle;
			}
		}
		path.remove(path.size() - 1);
		state[node] = 2;
		return List.of();
	}

	/**
	 * The types an agent makes available to the agents after it.
	 * @param agent the agent to inspect
	 * @return the types its steps and its goal produce
	 */
	static Set<String> producedTypes(AgentSpec agent) {
		Set<String> produced = new LinkedHashSet<>();
		for (ActionStepSpec step : agent.steps()) {
			addNonNull(produced, shortName(step.outputTypeName()));
		}
		if (agent.goal() != null) {
			addNonNull(produced, shortName(agent.goal().outputTypeName()));
		}
		return produced;
	}

	/**
	 * The types an agent asks the agents before it for.
	 * @param agent the agent to inspect
	 * @return the types its steps consume, {@code UserInput} excluded
	 */
	static Set<String> consumedTypes(AgentSpec agent) {
		Set<String> consumed = new LinkedHashSet<>();
		for (ActionStepSpec step : agent.steps()) {
			for (String input : step.inputTypeNames()) {
				addNonNull(consumed, shortName(input));
			}
		}
		consumed.remove(USER_INPUT);
		return consumed;
	}

	private static Set<String> referencedTypes(AgentSpec agent) {
		Set<String> referenced = new LinkedHashSet<>(consumedTypes(agent));
		referenced.addAll(producedTypes(agent));
		return referenced;
	}

	private static boolean consumesAvailable(AgentSpec agent, Set<String> available) {
		for (ActionStepSpec step : agent.steps()) {
			for (String input : step.inputTypeNames()) {
				if (available.contains(shortName(input))) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean inputsSatisfied(ActionStepSpec step, Set<String> available) {
		if (step.inputTypeNames().isEmpty()) {
			return false;
		}
		return step.inputTypeNames().stream().allMatch((input) -> available.contains(shortName(input)));
	}

	private static void addNonNull(Set<String> set, String value) {
		if (value != null) {
			set.add(value);
		}
	}

	/**
	 * The short name of a declared type: a step may name a type by its fully-qualified
	 * name, and both spellings are the same hand-off.
	 * @param typeName the declared type name
	 * @return the type name without its package, or {@code null} when none was declared
	 */
	static String shortName(String typeName) {
		if (typeName == null || typeName.isBlank()) {
			return null;
		}
		int idx = typeName.lastIndexOf('.');
		return (idx >= 0) ? typeName.substring(idx + 1) : typeName;
	}

	private record Flow(List<String> issues, boolean connected) {
	}

}
