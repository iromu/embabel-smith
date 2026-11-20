/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.innerloop;

import java.util.List;

/**
 * One rule of a detection policy: a reason to record, the condition that raises it, and
 * the exclusions that veto it. This is the parsed form of a {@code TRIGGER} line in a
 * policy prompt (the grammar is in {@code src/main/resources/policies/README.md}), which
 * is what lets a mutation operator change one thing about a policy and be named for it.
 *
 * @param trigger the reason the policy records when this rule fires
 * @param when the condition that raises it
 * @param unless conditions that veto it — the policy blocks, in the seed's case
 * @author Ivan Rodriguez
 */
public record PolicyRule(String trigger, Condition when, List<Condition> unless) {

	/**
	 * A rule condition with no operand, e.g. {@code failed}.
	 * @param kind the condition
	 * @return the condition.
	 */
	public static Condition condition(Condition.Kind kind) {
		return new Condition(kind, null);
	}

	/**
	 * A rule condition that matches against text, e.g. {@code error_prefix "…"}.
	 * @param kind the condition
	 * @param operand the text to match
	 * @return the condition.
	 */
	public static Condition condition(Condition.Kind kind, String operand) {
		return new Condition(kind, operand);
	}

	/**
	 * A condition a rule tests against a case.
	 *
	 * @param kind which test
	 * @param operand the text it matches, or null for a condition that needs none
	 * @author Ivan Rodriguez
	 */
	public record Condition(Kind kind, String operand) {

		/**
		 * The tests a policy prompt can name. Each is one JavaScript expression over a
		 * case, emitted by {@link PolicyCompiler}.
		 *
		 * @author Ivan Rodriguez
		 */
		public enum Kind {

			/** The run did not achieve its goal. */
			FAILED,

			/** The recorded error starts with the operand — the product's own rule. */
			ERROR_PREFIX,

			/**
			 * The recorded error mentions the operand somewhere. A keyword, and the
			 * caricature.
			 */
			ERROR_CONTAINS,

			/** The pack's content hash differs from the sidecar's. */
			HASH_DIFFERS,

			/** A recorded re-eval is below the recorded baseline. */
			REEVAL_BELOW_BASELINE,

			/**
			 * Any step exceeded {@link PolicyCompiler#SLOW_STEP_MILLIS}. Latency, which
			 * the corpus labels as not a defect.
			 */
			SLOW_STEP

		}

		/**
		 * Whether this condition matches text.
		 * @return true when an operand was given.
		 */
		public boolean hasOperand() {
			return this.operand != null;
		}

	}

}
