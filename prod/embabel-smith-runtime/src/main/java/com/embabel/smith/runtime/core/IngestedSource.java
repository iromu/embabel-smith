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
