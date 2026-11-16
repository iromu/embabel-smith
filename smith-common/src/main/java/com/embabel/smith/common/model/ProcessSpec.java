/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.common.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * A business process decomposed into {@link AgentSpec} agents: the named, ordered list of
 * packs the builder produces as its multi-agent candidate and the runtime composes into a
 * single run.
 *
 * <p>
 * It is a <em>directory of packs</em>, not a single file — the mirror of
 * {@link AgentSpec}, which is a directory itself: <pre>
 *   &lt;process&gt;/
 *     process.yml           ProcessSpecManifest (name, version, pack names in run order)
 *     &lt;agent&gt;/             one agent-spec directory per agent
 * </pre>
 *
 * <p>
 * The order is load-bearing: the first agent takes the operator's {@code UserInput}, the
 * last agent's goal is the process's final deliverable, and an agent may only consume a
 * type an earlier agent produced. Agents hand off to each other through shared
 * intermediate types — a type an earlier agent's step or goal outputs and a later agent's
 * step consumes. A one-agent process is the flat baseline.
 *
 * <p>
 * This aggregate is the in-memory form of that directory; see
 * {@code com.embabel.smith.common.io.ProcessSpecReader} / {@code ProcessSpecWriter} for
 * the (de)serialization.
 *
 * @param manifest the manifest of the process (name, version, pack names in run order)
 * @param agents the agents of the process, in run order
 * @author Ivan Rodriguez
 */
@JsonInclude(Include.NON_NULL)
public record ProcessSpec(ProcessSpecManifest manifest, List<AgentSpec> agents) {

	public ProcessSpec {
		agents = (agents != null) ? List.copyOf(agents) : List.of();
	}

	/**
	 * The process of a named list of agents, recording their run order in the manifest.
	 * @param process the process name.
	 * @param agents the agents in run order.
	 */
	public ProcessSpec(String process, List<AgentSpec> agents) {
		this(new ProcessSpecManifest(process, "1.0.0", packNames(agents)), agents);
	}

	/**
	 * A process of one agent — the flat baseline the multi-agent candidate is compared
	 * against.
	 * @param process the process name.
	 * @param agent the single agent.
	 * @return the one-agent process.
	 */
	public static ProcessSpec single(String process, AgentSpec agent) {
		return new ProcessSpec(process, List.of(agent));
	}

	/** The process name. */
	public String process() {
		return (this.manifest != null) ? this.manifest.process() : null;
	}

	/** The version of the process. */
	public String version() {
		return (this.manifest != null) ? this.manifest.version() : null;
	}

	/** The total number of action steps across every agent. */
	public int totalSteps() {
		return this.agents.stream().mapToInt((a) -> a.steps().size()).sum();
	}

	/** The last agent's goal output type — the process's final deliverable. */
	public String finalOutputType() {
		if (this.agents.isEmpty()) {
			return null;
		}
		AgentSpec last = this.agents.get(this.agents.size() - 1);
		return (last.goal() != null) ? last.goal().outputTypeName() : null;
	}

	/**
	 * The same process with its agents in a new order, with the manifest's run order
	 * rewritten to match. Used by the cycle repair.
	 * @param reordered the agents in their new run order.
	 * @return the reordered process.
	 */
	public ProcessSpec withAgents(List<AgentSpec> reordered) {
		return new ProcessSpec(process(), reordered);
	}

	private static List<String> packNames(List<AgentSpec> agents) {
		List<String> names = new ArrayList<>();
		if (agents != null) {
			agents.forEach((a) -> names.add(a.name()));
		}
		return names;
	}

}
