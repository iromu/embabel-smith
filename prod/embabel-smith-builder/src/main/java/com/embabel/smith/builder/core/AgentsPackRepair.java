/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.core;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentsPack;
import com.embabel.smith.common.validation.AgentsPackValidationResult;
import com.embabel.smith.common.validation.AgentsPackValidator;

/**
 * The bounded repair for a process whose validation found a cycle.
 *
 * <p>
 * The single-shot model writes the agents in the order it happens to narrate them, and
 * that order often contradicts the data flow it just described — an agent listed before
 * the one whose output it consumes. Reordering the agents topologically over the
 * data-flow graph settles that, and the process re-validates. What no reorder can settle
 * is a process whose <em>data flow itself</em> is circular: agents that genuinely need
 * each other's outputs. That is reported, not worked around — the build blocks rather
 * than publishing a process the planner cannot run.
 *
 * <p>
 * The loop is bounded twice over: by {@link #MAX_ATTEMPTS}, and because a reorder that
 * produces an order the process already has has nothing left to move.
 *
 * @author Ivan Rodriguez
 */
public final class AgentsPackRepair {

	/** The number of reorders attempted before a cycle is declared unrepairable. */
	public static final int MAX_ATTEMPTS = 3;

	private AgentsPackRepair() {
	}

	/**
	 * Reorders a cycled process and re-validates it until the cycle is gone or the bound
	 * is reached.
	 * @param agentsPack the agents-pack validation found a cycle in
	 * @param validation the validation that found it
	 * @return the process as repaired (possibly unchanged), its re-validation, and how
	 * many reorders it took
	 */
	public static Result repair(AgentsPack agentsPack, AgentsPackValidationResult validation) {
		AgentsPack current = agentsPack;
		AgentsPackValidationResult currentValidation = validation;
		int attempts = 0;
		while (currentValidation.hasCycle() && attempts < MAX_ATTEMPTS) {
			List<AgentSpec> ordered = dataOrder(current.agents());
			if (ordered == null || ordered.equals(current.agents())) {
				break;
			}
			attempts++;
			current = current.withAgents(ordered);
			currentValidation = new AgentsPackValidator().validate(current);
		}
		return new Result(current, currentValidation, attempts, attempts > 0 && !currentValidation.hasCycle());
	}

	/**
	 * The agents in an order that runs every producer before its consumers, preferring
	 * the declared order where the data flow leaves a choice.
	 * @param agents the agents of the process, in run order
	 * @return the topologically ordered agents, or {@code null} when the data flow itself
	 * is circular and no order can satisfy it
	 */
	private static List<AgentSpec> dataOrder(List<AgentSpec> agents) {
		int n = agents.size();
		List<List<Integer>> edges = AgentsPackValidator.dependencyEdges(agents, false);
		int[] incoming = new int[n];
		for (List<Integer> outgoing : edges) {
			for (int to : outgoing) {
				incoming[to]++;
			}
		}
		PriorityQueue<Integer> ready = new PriorityQueue<>();
		for (int i = 0; i < n; i++) {
			if (incoming[i] == 0) {
				ready.add(i);
			}
		}
		List<AgentSpec> ordered = new ArrayList<>();
		while (!ready.isEmpty()) {
			int node = ready.poll();
			ordered.add(agents.get(node));
			for (int to : edges.get(node)) {
				if (--incoming[to] == 0) {
					ready.add(to);
				}
			}
		}
		// Agents left over are the ones no order can place after their own producers.
		return (ordered.size() == n) ? ordered : null;
	}

	/**
	 * The outcome of a repair attempt.
	 *
	 * @param agentsPack the agents-pack as repaired — the input when the cycle could not
	 * be broken
	 * @param validation the re-validation of the repaired process
	 * @param attempts the number of reorders performed
	 * @param repaired whether the cycle is gone
	 */
	public record Result(AgentsPack agentsPack, AgentsPackValidationResult validation, int attempts, boolean repaired) {
	}

}
