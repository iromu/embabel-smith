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
 * An agents-pack: a business process decomposed into {@link AgentSpec} agents. It is the
 * named, ordered list of packs the builder produces as its multi-agent candidate and the
 * runtime composes into a single run.
 *
 * <p>
 * It is a <em>directory of packs</em>, not a single file — the mirror of
 * {@link AgentSpec}, which is a directory itself: <pre>
 *   &lt;agents-pack&gt;/
 *     process.yml           AgentsPackManifest (name, version, pack names in run order)
 *     &lt;agent&gt;/             one agent-spec directory per agent
 * </pre>
 *
 * <p>
 * The order is load-bearing: the first agent takes the operator's {@code UserInput}, the
 * last agent's goal is the agents-pack's final deliverable, and an agent may only consume
 * a type an earlier agent produced. Agents hand off to each other through shared
 * intermediate types — a type an earlier agent's step or goal outputs and a later agent's
 * step consumes. A one-agent pack is the flat baseline.
 *
 * <p>
 * This aggregate is the in-memory form of that directory; see
 * {@code com.embabel.smith.common.io.AgentsPackReader} / {@code AgentsPackWriter} for the
 * (de)serialization.
 *
 * @param manifest the manifest of the agents-pack (name, version, pack names in run
 * order)
 * @param agents the agents of the agents-pack, in run order
 * @author Ivan Rodriguez
 */
@JsonInclude(Include.NON_NULL)
public record AgentsPack(AgentsPackManifest manifest, List<AgentSpec> agents) {

	public AgentsPack {
		agents = (agents != null) ? List.copyOf(agents) : List.of();
	}

	/**
	 * The agents-pack of a named list of agents, recording their run order in the
	 * manifest.
	 * @param process the name of the agents-pack.
	 * @param agents the agents in run order.
	 */
	public AgentsPack(String process, List<AgentSpec> agents) {
		this(new AgentsPackManifest(process, "1.0.0", packNames(agents)), agents);
	}

	/**
	 * An agents-pack of one agent — the flat baseline the multi-agent candidate is
	 * compared against.
	 * @param process the name of the agents-pack.
	 * @param agent the single agent.
	 * @return the one-agent agents-pack.
	 */
	public static AgentsPack single(String process, AgentSpec agent) {
		return new AgentsPack(process, List.of(agent));
	}

	/** The agents-pack name, read from the manifest's frozen {@code process} key. */
	public String process() {
		return (this.manifest != null) ? this.manifest.process() : null;
	}

	/** The version of the agents-pack. */
	public String version() {
		return (this.manifest != null) ? this.manifest.version() : null;
	}

	/** The total number of action steps across every agent. */
	public int totalSteps() {
		return this.agents.stream().mapToInt((a) -> a.steps().size()).sum();
	}

	/** The last agent's goal output type — the agents-pack's final deliverable. */
	public String finalOutputType() {
		if (this.agents.isEmpty()) {
			return null;
		}
		AgentSpec last = this.agents.get(this.agents.size() - 1);
		return (last.goal() != null) ? last.goal().outputTypeName() : null;
	}

	/**
	 * The same agents-pack with its agents in a new order, with the manifest's run order
	 * rewritten to match. Used by the cycle repair.
	 * @param reordered the agents in their new run order.
	 * @return the reordered agents-pack.
	 */
	public AgentsPack withAgents(List<AgentSpec> reordered) {
		return new AgentsPack(process(), reordered);
	}

	private static List<String> packNames(List<AgentSpec> agents) {
		List<String> names = new ArrayList<>();
		if (agents != null) {
			agents.forEach((a) -> names.add(a.name()));
		}
		return names;
	}

}
