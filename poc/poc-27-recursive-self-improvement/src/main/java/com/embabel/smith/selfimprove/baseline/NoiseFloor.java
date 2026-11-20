/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.baseline;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import com.embabel.smith.selfimprove.referee.FrozenTaskSet;

/**
 * Task 2.5's evidence: the repeat-to-repeat noise floor of the unchanged process,
 * measured on the scorer the gate uses.
 *
 * <p>
 * <b>{@link #gateFloor()} is the primary number and the only threshold.</b> Task 7.4
 * gates a candidate's delta against it, and the delta is a difference of the product's
 * substring score, so the floor has to be a spread of that same score: a floor measured
 * on one scorer and a delta gated on the other is a unit mismatch nobody would notice.
 * The human label's spread is recorded beside it for comparison, never as a threshold.
 *
 * <p>
 * <b>What this measures, in its own words.</b> Scoring is deterministic — the primary arm
 * is a substring test and the human label is each task file's own check — so this is
 * <em>not</em> a scorer's variance. It is the variance of a live model's <em>answers</em>
 * to the same six prompts, asked again with nothing changed. {@link #measures()} states
 * that in the file, because a reader who assumes it is the scorer's noise will draw the
 * wrong conclusion from a zero.
 *
 * <p>
 * <b>A zero is reported as zero.</b> If every repeat scores the same, the floor is
 * {@code 0.0} and the record says so; nothing here invents a floor to be conservative
 * about. The consequence belongs to task 7.4, which gates a delta against this number:
 * with a floor of zero, any strictly positive delta clears the floor and the only thing
 * standing between model noise and a claimed improvement is that the frozen answers
 * happened to agree this time. That is stated in {@link #measures()} rather than left for
 * a reader to work out.
 *
 * @param measures what the number is a variance of, and what a zero means for task 7.4
 * @param primaryFloorIs which field is the threshold, and what it must be comparable to
 * @param repeats how many times the unchanged process was scored
 * @param gateFloor the primary floor: the spread of the product's substring score across
 * the repeats
 * @param gateScores the primary score, one entry per repeat, in repeat order
 * @param gateFlippingTaskIds the tasks whose verdict under the primary scorer was not the
 * same in every repeat, which is where the primary floor came from
 * @param humanLabelFloor the spread of each task file's full recorded verdict, for
 * comparison only
 * @param humanLabelScores the human label, one entry per repeat, in repeat order
 * @param humanLabelFlippingTaskIds the tasks whose verdict under the human label was not
 * the same in every repeat; may name tasks the primary floor cannot see
 * @author Ivan Rodriguez
 */
public record NoiseFloor(String measures, String primaryFloorIs, int repeats, double gateFloor, List<Double> gateScores,
		List<String> gateFlippingTaskIds, double humanLabelFloor, List<Double> humanLabelScores,
		List<String> humanLabelFlippingTaskIds) {

	/**
	 * How many times the unchanged process has to be scored for a spread to mean
	 * anything. It is a constant rather than a flag because task 7.6 wants the budget
	 * declared before the first call: the paid run costs the frozen task count times
	 * this.
	 */
	public static final int MIN_REPEATS = 3;

	private static final String MEASURES = "answer variance across repeats, not scorer variance: both scorers are"
			+ " deterministic, so the only thing that can move between repeats is what the live model answered to the"
			+ " same prompts. A floor of 0.0 means every repeat scored identically and is reported as 0.0, not raised"
			+ " for safety; task 7.4 must then know that a delta of any size clears a floor of zero and that"
			+ " repeat-to-repeat agreement here is the only evidence against noise it has.";

	private static final String PRIMARY_IS = "gateFloor — the spread of the product's own substring score, the"
			+ " scorer GateChain calls. It is the only threshold: task 7.4's delta is a difference of that same"
			+ " score, so comparing it to a spread of any other score would be a unit mismatch. humanLabelFloor is"
			+ " recorded beside it and is never a threshold.";

	public NoiseFloor {
		gateScores = List.copyOf(gateScores);
		gateFlippingTaskIds = List.copyOf(gateFlippingTaskIds);
		humanLabelScores = List.copyOf(humanLabelScores);
		humanLabelFlippingTaskIds = List.copyOf(humanLabelFlippingTaskIds);
	}

	/**
	 * Reduces repeated scorings of the same unchanged process to a floor.
	 * @param tasks the frozen set every repeat was scored against
	 * @param passes one entry per repeat, in repeat order, each the same process scored
	 * again
	 * @return the floor per arm, with the tasks that moved under each
	 * @throws IllegalStateException when fewer than {@link #MIN_REPEATS} repeats were
	 * scored, because a spread over two runs is not a noise floor and the file would
	 * claim to be one
	 */
	public static NoiseFloor of(FrozenTaskSet tasks, List<ScoredAnswers> passes) {
		if (passes.size() < MIN_REPEATS) {
			throw new IllegalStateException("a noise floor needs at least " + MIN_REPEATS + " repeats of the same"
					+ " unchanged process, and " + passes.size() + " were scored. A floor measured over fewer is not"
					+ " evidence of a floor, and task 7.4 gates every later delta against it.");
		}
		List<Double> gate = passes.stream().map(ScoredAnswers::gateScore).toList();
		List<Double> humanLabel = passes.stream().map(ScoredAnswers::fullConditionScore).toList();
		return new NoiseFloor(MEASURES, PRIMARY_IS, passes.size(), spread(gate), gate,
				flipping(tasks, passes, ScoredAnswers::gateFailingTaskIds), spread(humanLabel), humanLabel,
				flipping(tasks, passes, ScoredAnswers::fullConditionFailingTaskIds));
	}

	/**
	 * Whether the primary score moved between repeats. The human label moving on its own
	 * is not an answer to this question, because the label is not the threshold.
	 * @return {@code true} when the primary floor is above zero
	 */
	public boolean answersMoved() {
		return this.gateFloor > 0.0;
	}

	private static double spread(List<Double> scores) {
		return scores.stream().mapToDouble(Double::doubleValue).max().orElseThrow()
				- scores.stream().mapToDouble(Double::doubleValue).min().orElseThrow();
	}

	/**
	 * The tasks that failed in some repeats and not in others under one scorer: the union
	 * of that scorer's failing sets minus their intersection, in task order. Naming them
	 * is what makes a non-zero floor attributable — a reader can see which question the
	 * model answers two different ways — and a task that moves under the human label
	 * alone is visible here as exactly that.
	 * @param tasks the frozen set
	 * @param passes the per-repeat scorings
	 * @param failing which arm's failing identifiers to compare
	 * @return the identifiers whose verdict under that scorer was not identical in every
	 * repeat
	 */
	private static List<String> flipping(FrozenTaskSet tasks, List<ScoredAnswers> passes,
			Function<ScoredAnswers, List<String>> failing) {
		Set<String> failedInSome = new LinkedHashSet<>();
		Set<String> failedInEvery = new LinkedHashSet<>(tasks.ids());
		for (ScoredAnswers pass : passes) {
			Set<String> failed = new LinkedHashSet<>(failing.apply(pass));
			failedInSome.addAll(failed);
			failedInEvery.retainAll(failed);
		}
		Set<String> flipped = new LinkedHashSet<>(failedInSome);
		flipped.removeAll(failedInEvery);
		List<String> ordered = new ArrayList<>();
		for (String id : tasks.ids()) {
			if (flipped.contains(id)) {
				ordered.add(id);
			}
		}
		return List.copyOf(ordered);
	}

}
