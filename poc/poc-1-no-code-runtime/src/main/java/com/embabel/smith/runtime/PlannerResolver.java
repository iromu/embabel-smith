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

package com.embabel.smith.runtime;

import com.embabel.agent.api.common.PlannerType;
import com.embabel.smith.common.model.AgentSpec;

/**
 * Resolves which planner a run should use.
 *
 * <p>
 * Precedence: an explicit per-run choice (e.g. {@code builder.planner}) &gt; the pack's
 * {@code manifest.defaultPlanner} &gt; {@link PlannerType#GOAP}. This keeps the planner
 * selectable per run while defaulting to the deterministic GOAP planner.
 *
 * @author Ivan Rodriguez
 */
public class PlannerResolver {

	public PlannerType resolve(String runOverride, AgentSpec pack) {
		String chosen = firstNonBlank(runOverride,
				(pack == null || pack.manifest() == null) ? null : pack.manifest().defaultPlanner());
		if (chosen == null) {
			return PlannerType.GOAP;
		}
		try {
			return PlannerType.valueOf(chosen.trim().toUpperCase());
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException(
					"Unknown planner '" + chosen + "'. Supported: "
							+ String.join(", ", java.util.Arrays.stream(PlannerType.values()).map(Enum::name).toList()),
					ex);
		}
	}

	private static String firstNonBlank(String... candidates) {
		for (String c : candidates) {
			if (c != null && !c.isBlank()) {
				return c;
			}
		}
		return null;
	}

}
