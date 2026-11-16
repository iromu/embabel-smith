/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.core;

import java.util.List;

import com.embabel.agent.core.Agent;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.specsource.ResolvedSpecSource;

/**
 * The outcome of ingesting a resolved spec source into the platform: the loaded packs,
 * the deployed agent holding all of their goals, and the source they came from.
 *
 * @param source the resolved spec source the packs came from
 * @param packs the loaded agent specs
 * @param agent the deployed agent holding all of the packs' goals
 * @author Ivan Rodriguez
 */
public record IngestedSource(ResolvedSpecSource source, List<AgentSpec> packs, Agent agent) {

	public IngestedSource {
		packs = List.copyOf(packs);
	}

	/** The goal names exported by the ingested packs. */
	public List<String> goalNames() {
		return this.packs.stream().map((pack) -> pack.goal().name()).toList();
	}

	/** The pack that exports the named goal. */
	public AgentSpec packForGoal(String goalName) {
		return this.packs.stream()
			.filter((pack) -> goalName.equals(pack.goal().name()))
			.findFirst()
			.orElseThrow(() -> new GoalNotFoundException(goalName, goalNames()));
	}
}
