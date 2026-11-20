/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.outerloop;

import java.util.List;

/**
 * What a finished run is allowed to say about itself, decided by how many generations it
 * actually completed.
 *
 * <p>
 * This class exists because "recursive self-improvement" is a claim about a
 * <em>series</em> and a run of one generation does not have one. With a single completed
 * generation there was exactly one improver, which was never itself improved: nothing
 * improved a thing that had itself been improved, so the run is one round of
 * hill-climbing and the recursive part of the claim is untested — not false, not weak,
 * untested. Saying that out loud is the difference between a POC that measured something
 * and one that advertised something.
 *
 * @param completedGenerations how many generations reached a score
 * @param shape what the run was, in words
 * @param recursiveClaim whether the recursive part of the claim was exercised
 * @param statement the run's own closing sentence
 * @author Ivan Rodriguez
 */
public record RunOutcome(int completedGenerations, String shape, String recursiveClaim, String statement) {

	/** The recursive claim was never exercised: no improver was ever improved. */
	public static final String UNTESTED = "untested";

	/** A single generation: one improver, one step, no series. */
	public static final String HILL_CLIMBING = "one round of hill-climbing";

	/**
	 * More than one generation: each generation's improver came from the last one's
	 * output.
	 */
	public static final String DERIVATIVE_SERIES = "derivative series";

	/** Nothing scored, so nothing is claimed. */
	public static final String NOTHING_SCORED = "nothing scored";

	/**
	 * Reads the shape off the completed generations.
	 * @param generations the generation reports, in order
	 * @return the outcome the run is entitled to report.
	 */
	public static RunOutcome of(List<GenerationReport> generations) {
		int completed = generations.size();
		if (completed == 0) {
			return new RunOutcome(0, NOTHING_SCORED, UNTESTED,
					"No generation reached a score, so this run measured nothing and claims nothing.");
		}
		if (completed == 1) {
			return new RunOutcome(1, HILL_CLIMBING, UNTESTED,
					"One completed generation is one round of hill-climbing: a single improver proposed variants"
							+ " and the best was picked. Nothing improved an improver, so the recursive claim is"
							+ " untested by this run — it is not disproof either, and a reader who wants it tested"
							+ " needs a second generation whose improver was derived from the first.");
		}
		return new RunOutcome(completed, DERIVATIVE_SERIES, "claimed for " + completed + " generations, no further",
				completed + " completed generations, each generation's improver derived from the variant the"
						+ " previous generation selected: that is improvement of an improver, and it is claimed for"
						+ " these generations and no further. Nothing here claims improvement of the fixed interpreter,"
						+ " which is the boundary evidence/MUTABILITY.md draws and this run cannot cross.");
	}

}
