/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.core;

import java.util.List;
import java.util.Optional;

import com.embabel.agent.core.Agent;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.specsource.ResolvedSpecSource;

/**
 * The outcome of ingesting a resolved spec source into the platform: the loaded packs,
 * the deployed agent holding all of their goals, the source they came from, and the packs
 * that were left out.
 *
 * <p>
 * The excluded packs are carried rather than dropped: a run that quietly lost a pack
 * produces a plan nobody can explain, so the exclusion is named wherever the source
 * reports what it skipped.
 *
 * @param source the resolved spec source the packs came from
 * @param packs the loaded agent specs
 * @param excluded the packs read from the source but not deployed, named and with the
 * goal each would have exported
 * @param agent the deployed agent holding all of the packs' goals
 * @author Ivan Rodriguez
 */
public record IngestedSource(ResolvedSpecSource source, List<AgentSpec> packs, List<ExcludedPack> excluded,
		Agent agent) {

	public IngestedSource {
		packs = List.copyOf(packs);
		excluded = List.copyOf(excluded);
	}

	/** The goal names exported by the ingested packs. */
	public List<String> goalNames() {
		return this.packs.stream().map((pack) -> pack.goal().name()).toList();
	}

	/**
	 * The excluded pack that alone exported the named goal, if any — the difference
	 * between a goal nobody exported and one a retirement took away.
	 * @param goalName the goal a run asked for
	 * @return the excluded pack that exported it.
	 */
	public Optional<ExcludedPack> excludedPackForGoal(String goalName) {
		return this.excluded.stream().filter((pack) -> goalName.equals(pack.goalName())).findFirst();
	}

	/** The pack that exports the named goal. */
	public AgentSpec packForGoal(String goalName) {
		return this.packs.stream()
			.filter((pack) -> goalName.equals(pack.goal().name()))
			.findFirst()
			.orElseThrow(() -> new GoalNotFoundException(goalName, goalNames()));
	}

	/**
	 * A pack read from the source and deliberately not deployed.
	 *
	 * @param name the pack's name
	 * @param goalName the goal it would have exported
	 */
	public record ExcludedPack(String name, String goalName) {
	}
}
