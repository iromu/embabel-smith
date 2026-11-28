/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.core;

import java.util.Arrays;

import com.embabel.agent.api.common.PlannerType;
import com.embabel.smith.common.model.AgentSpec;

/**
 * Resolves which planner a run should use, and says which setting produced it.
 *
 * <p>
 * Precedence: an explicit per-run choice &gt; the goal pack's
 * {@code manifest.defaultPlanner} &gt; {@link PlannerType#GOAP}. This keeps the planner
 * selectable per run while defaulting to the deterministic GOAP planner.
 *
 * <p>
 * {@link #resolve} answers the question a run asks — which strategy to deploy with — and
 * {@link #decide} answers the one a reader asks two settings later: there are two places
 * a planner can be named, and a report that only prints the strategy cannot say whether
 * the run's flag won or the pack's own default did. The answer travels as a
 * {@link Decision} so the run's report can state it rather than leave the precedence to
 * be re-derived from two files.
 *
 * @author Ivan Rodriguez
 */
public class PlannerResolver {

	/**
	 * The planner to use, without saying where it came from.
	 * @param runOverride an explicit per-run name, or {@code null}
	 * @param goalPack the pack that owns the goal, or {@code null}
	 * @return the planner
	 * @throws IllegalArgumentException when a name is not a planner the platform knows
	 */
	public PlannerType resolve(String runOverride, AgentSpec goalPack) {
		return decide(runOverride, goalPack).planner();
	}

	/**
	 * The planner to use and which of the two settings won.
	 * @param runOverride an explicit per-run name, or {@code null}
	 * @param goalPack the pack that owns the goal, or {@code null}
	 * @return the decision, whose {@link Decision#describe()} is the line a report shows
	 * @throws IllegalArgumentException when a name is not a planner the platform knows
	 */
	public Decision decide(String runOverride, AgentSpec goalPack) {
		String packDefault = declaredDefault(goalPack);
		String chosen = firstNonBlank(runOverride, packDefault);
		if (chosen == null) {
			return new Decision(PlannerType.GOAP, Origin.PLATFORM_DEFAULT, null);
		}
		Origin origin = isNotBlank(runOverride) ? Origin.RUN_CHOICE : Origin.PACK_DEFAULT;
		return new Decision(known(chosen), origin, packDefault);
	}

	private static String declaredDefault(AgentSpec goalPack) {
		if (goalPack == null || goalPack.manifest() == null) {
			return null;
		}
		return goalPack.manifest().defaultPlanner();
	}

	private static PlannerType known(String chosen) {
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
			if (isNotBlank(c)) {
				return c;
			}
		}
		return null;
	}

	private static boolean isNotBlank(String value) {
		return value != null && !value.isBlank();
	}

	private static boolean isBlank(String value) {
		return !isNotBlank(value);
	}

	/**
	 * Which of the two settings named the planner a run used.
	 */
	public enum Origin {

		/** The per-run flag named it, whatever the pack's manifest says. */
		RUN_CHOICE,
		/** The goal pack's manifest named it and no run flag was given. */
		PACK_DEFAULT,
		/** Neither said, so the platform's deterministic default applies. */
		PLATFORM_DEFAULT

	}

	/**
	 * The resolved planner together with the setting that produced it.
	 *
	 * @param planner the planner to deploy with
	 * @param origin which setting named it
	 * @param packDefault the pack's own declared default, when it declared one
	 * @author Ivan Rodriguez
	 */
	public record Decision(PlannerType planner, Origin origin, String packDefault) {

		/**
		 * The line a run's report carries about this choice: the strategy, where it came
		 * from, and — when a run flag overrode a pack default — the default it overrode,
		 * so the reader is not left to diff two settings.
		 * @return one line
		 */
		public String describe() {
			if (this.origin == Origin.RUN_CHOICE) {
				return describeRunChoice();
			}
			if (this.origin == Origin.PACK_DEFAULT) {
				return "planner: " + strategy() + " (the pack manifest's defaultPlanner)";
			}
			return "planner: " + strategy() + " (neither the run nor the pack named one)";
		}

		private String describeRunChoice() {
			if (isBlank(this.packDefault)) {
				return "planner: " + strategy() + " (the run's choice; the pack named no default)";
			}
			return "planner: " + strategy() + " (the run's choice, overriding the pack's default "
					+ this.packDefault.trim() + ")";
		}

		private String strategy() {
			// The 1.5.4 DefaultPlannerFactory maps SUPERVISOR to AStarGoapPlanner.
			// The name is not a fourth algorithm, so the line names the one that ran.
			return (this.planner == PlannerType.SUPERVISOR) ? this.planner.name() + " [A* GOAP]" : this.planner.name();
		}

	}

}
