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

package com.embabel.smith.common.model;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * A read-only diagnostic for a compiled draft's goal shape — the measurable signal behind
 * doc 02 caveat #2 ("LLM-authored spec quality is weak at the source"). It classifies
 * whether the draft's exported goal is runnable or degenerate, and says exactly why. It
 * does <em>not</em> modify the draft: the builder's {@code normalizeGoalOutput} repairs
 * the deterministic degenerate shapes (void / no-op / scalar goals); this classifier is
 * the measurement layer that makes the weakness observable (POC 2 reports the raw draft;
 * POC 3 logs the draft before and after normalizing).
 *
 * <p>
 * The one shape it detects that the normalizer does not auto-repair is the
 * <em>orphan</em> goal — the goal's output type is produced by no action, so the GOAP
 * plan cannot reach it and STUCKs. Flagging it is the point: whether it is fixable
 * depends on the compiler having produced a coherent terminal action, which is exactly
 * the residual weakness only the eval loop (POC 9) repairs.
 *
 * @author Ivan Rodriguez
 */
public final class DraftQuality {

	/**
	 * Scalar types a goal output cannot be (the runtime returns a concrete object, not a
	 * raw scalar).
	 */
	private static final Set<String> SCALAR_TYPES = Set.of("string", "integer", "long", "number", "double", "float",
			"boolean", "char", "java.lang.String", "java.lang.Integer", "java.lang.Long", "java.lang.Double",
			"java.lang.Float", "java.lang.Boolean", "java.lang.Character");

	/**
	 * The framework's entry types — a goal that "outputs" one of these just echoes its
	 * input.
	 */
	private static final Set<String> ENTRY_TYPES = Set.of("com.embabel.agent.domain.io.UserInput",
			"com.embabel.agent.domain.io.SystemInput");

	private DraftQuality() {
	}

	/**
	 * Classify a draft's goal shape.
	 * @param goalOutput the goal's declared output type, or {@code null} if the draft has
	 * no goal
	 * @param actionOutputTypes the output types of the draft's actions (used to detect an
	 * orphan goal)
	 * @return the classification of the draft's goal shape
	 */
	public static Assessment assess(String goalOutput, Collection<String> actionOutputTypes) {
		if (goalOutput == null || goalOutput.isBlank()) {
			return new Assessment(Verdict.NO_GOAL, "the draft has no goal output type", true);
		}
		if (isVoid(goalOutput)) {
			return new Assessment(Verdict.VOID_GOAL,
					"the goal outputs void, which never registers in the GOAP world state", true);
		}
		if (ENTRY_TYPES.contains(goalOutput)) {
			return new Assessment(Verdict.NO_OP_GOAL,
					"the goal echoes its input (" + goalOutput + ") and is trivially satisfied with no output", true);
		}
		if (SCALAR_TYPES.contains(goalOutput)) {
			return new Assessment(Verdict.SCALAR_GOAL,
					"the goal outputs the scalar type '" + goalOutput + "', not a concrete returnable type", true);
		}
		Collection<String> produced = (actionOutputTypes != null) ? actionOutputTypes : List.of();
		if (!produced.contains(goalOutput)) {
			return new Assessment(Verdict.ORPHAN_GOAL,
					"no action produces the goal's output type '" + goalOutput + "', so the plan cannot reach it",
					true);
		}
		return new Assessment(Verdict.HEALTHY, "the goal output '" + goalOutput + "' is produced by an action", false);
	}

	private static boolean isVoid(String type) {
		return "void".equals(type) || "java.lang.Void".equals(type);
	}

	/** The verdict for a draft's goal shape. */
	public enum Verdict {

		/**
		 * The goal output is a concrete type produced by at least one action — runnable.
		 */
		HEALTHY,
		/** No goal / no goal output type — there is nothing to run. */
		NO_GOAL,
		/**
		 * The goal outputs {@code void} — it never registers in the GOAP world state
		 * (plan STUCKs).
		 */
		VOID_GOAL,
		/**
		 * The goal echoes its own input ({@code UserInput}/{@code SystemInput}) — a
		 * no-op.
		 */
		NO_OP_GOAL,
		/**
		 * The goal outputs a scalar (string, number, ...) — not a concrete, returnable
		 * type.
		 */
		SCALAR_GOAL,
		/**
		 * No action produces the goal's output type — the plan cannot reach the goal
		 * (STUCKs).
		 */
		ORPHAN_GOAL

	}

	/**
	 * A classification of a draft's goal shape: the verdict, a human reason, and whether
	 * it is degenerate.
	 *
	 * @param verdict the classification verdict
	 * @param reason a human-readable explanation of the verdict
	 * @param degenerate whether the goal shape is degenerate
	 */
	public record Assessment(Verdict verdict, String reason, boolean degenerate) {
		public boolean isDegenerate() {
			return this.degenerate;
		}
	}
}
