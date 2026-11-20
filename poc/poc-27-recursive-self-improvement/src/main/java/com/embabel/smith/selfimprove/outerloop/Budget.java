/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.outerloop;

/**
 * The ceiling a run commits to before it spends anything, so that "how far did we get" is
 * answerable afterwards.
 *
 * <p>
 * Both halves are declared up front because both are consumable: model calls are paid
 * for, and generations are the unit the recursive claim is made in. A run that stops at
 * either boundary reports the budget it was given and names the measurements it did not
 * take, which is the only way a stopped run and a completed run can be told apart from
 * the report alone.
 *
 * @param maxModelCalls the most metered model calls the run may spend
 * @param maxGenerations the most generations the run may attempt
 * @author Ivan Rodriguez
 */
public record Budget(int maxModelCalls, int maxGenerations) {

	/**
	 * Declares the budget. Both limits are positive by construction: a run with no budget
	 * has nothing to report but an empty archive, and that is a caller mistake rather
	 * than a result.
	 * @param maxModelCalls the most metered model calls to spend
	 * @param maxGenerations the most generations to attempt
	 * @return the declared budget.
	 */
	public static Budget declare(int maxModelCalls, int maxGenerations) {
		if (maxModelCalls <= 0) {
			throw new IllegalArgumentException("Model-call budget must be positive, got " + maxModelCalls);
		}
		if (maxGenerations <= 0) {
			throw new IllegalArgumentException("Generation budget must be positive, got " + maxGenerations);
		}
		return new Budget(maxModelCalls, maxGenerations);
	}

	/**
	 * Whether another candidate's worth of calls still fits under the ceiling. Checked
	 * before the candidate is scored, because the calls are already spent by the time a
	 * score comes back.
	 * @param spent the metered calls spent so far
	 * @param costPerCandidate the calls one candidate costs
	 * @return true when one more candidate fits.
	 */
	public boolean admits(int spent, int costPerCandidate) {
		return spent + costPerCandidate <= this.maxModelCalls;
	}

	/**
	 * One-line form for the run report.
	 * @return the declared limits.
	 */
	public String describe() {
		return this.maxModelCalls + " model calls, " + this.maxGenerations + " generations";
	}

}
