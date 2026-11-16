/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.core;

import java.util.Arrays;

import com.embabel.agent.api.common.PlannerType;
import com.embabel.smith.common.model.AgentSpec;

/**
 * Resolves which planner a run should use.
 *
 * <p>
 * Precedence: an explicit per-run choice &gt; the goal pack's
 * {@code manifest.defaultPlanner} &gt; {@link PlannerType#GOAP}. This keeps the planner
 * selectable per run while defaulting to the deterministic GOAP planner.
 *
 * @author Ivan Rodriguez
 */
public class PlannerResolver {

	public PlannerType resolve(String runOverride, AgentSpec goalPack) {
		String chosen = firstNonBlank(runOverride,
				(goalPack == null || goalPack.manifest() == null) ? null : goalPack.manifest().defaultPlanner());
		if (chosen == null) {
			return PlannerType.GOAP;
		}
		try {
			return PlannerType.valueOf(chosen.trim().toUpperCase());
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("Unknown planner '" + chosen + "'. Supported: "
					+ String.join(", ", Arrays.stream(PlannerType.values()).map(Enum::name).toList()), ex);
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
