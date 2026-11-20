/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.baseline;

import java.util.List;

import com.embabel.smith.selfimprove.referee.FrozenTaskSet;

/**
 * Task 2.4's evidence: the score of the unmodified process against the frozen task set,
 * recorded against the scorer the improvement loop is gated by.
 *
 * <p>
 * <b>{@link #baselineScore()} is the primary number</b> — the product's own substring
 * score, the one {@code GateChain} computes through {@code EvalScorer} and the one task
 * 7.4's delta is measured against. That is a ruling, not a default: the loop has to
 * optimise the scorer the product actually has, or the POC measures a system that does
 * not exist.
 *
 * <p>
 * <b>{@link #humanLabelScore()} is recorded alongside it as the known gap.</b> Every
 * frozen task carries an {@code anyOf} or a {@code forbid} clause the substring scorer
 * cannot express ({@link FrozenTaskSet#narrowedTaskIds()}), and the human authoring in
 * each task file honours them. Where the two disagree, the primary baseline is the
 * <em>weaker</em> of the pair — a narrowed task the human label fails can pass the gate —
 * so {@link #divergingTaskIds()} names the tasks where that happens on this run, in the
 * record itself, rather than leaving it to be worked out from the two numbers.
 *
 * @param taskIds the frozen task identifiers, in task order
 * @param narrowedTaskIds the tasks carrying a condition the primary scorer cannot
 * express, so every one of them is a place the two scores can disagree
 * @param primaryBaselineIs what the primary field is, and who reads it
 * @param baselineScore the primary baseline: the product's substring score of the
 * unmodified process
 * @param baselineFailingTaskIds the tasks the primary scorer rejected
 * @param humanLabelIs what the second field is, and that it is the gap rather than the
 * pass mark
 * @param humanLabelScore the same answers under each task file's full recorded verdict
 * @param humanLabelFailingTaskIds the tasks that verdict rejected
 * @param divergingTaskIds the tasks the human label rejects and the primary baseline
 * accepts — where this run shows the primary baseline to be weaker than the label
 * @param modelCalls the calls this measurement spent, read off the meter
 * @param headroom whether the primary baseline leaves anything to improve
 * @param verdict what the run concluded, in the file, including the stop instruction when
 * there is no headroom and the divergence statement when the two scores disagree
 * @author Ivan Rodriguez
 */
public record Baseline(List<String> taskIds, List<String> narrowedTaskIds, String primaryBaselineIs,
		double baselineScore, List<String> baselineFailingTaskIds, String humanLabelIs, double humanLabelScore,
		List<String> humanLabelFailingTaskIds, List<String> divergingTaskIds, int modelCalls, boolean headroom,
		String verdict) {

	/**
	 * What the primary field is, stated in the evidence rather than inferred from a name.
	 */
	public static final String PRIMARY_IS = "the product's own substring score — EvalScorer, the scorer GateChain"
			+ " calls inside reeval. The gate and task 7.4's delta read this field.";

	/**
	 * What the second field is, stated in the evidence rather than inferred from a name.
	 */
	public static final String HUMAN_LABEL_IS = "the human label — each task file's own recorded verdict, honouring the"
			+ " anyOf/forbid clauses the substring scorer cannot express. Recorded as the known gap, not as the pass"
			+ " mark: the loop is gated by the primary field by design.";

	/**
	 * The pass mark the outer loop was authored expecting. It is design intent from
	 * {@code benchmark/tasks/README.md}, kept here so the measured number is read against
	 * the expectation it was written to replace rather than against nothing.
	 */
	public static final String AUTHORED_EXPECTATION = "2/6 — the figure benchmark/tasks/README.md records as design"
			+ " intent, which this measurement replaces";

	public Baseline {
		taskIds = List.copyOf(taskIds);
		narrowedTaskIds = List.copyOf(narrowedTaskIds);
		baselineFailingTaskIds = List.copyOf(baselineFailingTaskIds);
		humanLabelFailingTaskIds = List.copyOf(humanLabelFailingTaskIds);
		divergingTaskIds = List.copyOf(divergingTaskIds);
	}

	/**
	 * Records one measurement of the unmodified process.
	 * @param tasks the frozen set that was scored
	 * @param scores the primary substring score and the human label for that scoring
	 * @param modelCalls the calls the measurement spent, read off
	 * {@link com.embabel.smith.selfimprove.innerloop.ModelCallMeter} rather than counted
	 * by hand
	 * @return the record to write as {@code evidence/baseline.json}
	 */
	public static Baseline of(FrozenTaskSet tasks, ScoredAnswers scores, int modelCalls) {
		boolean headroom = scores.gateArmHasHeadroom();
		List<String> diverging = scores.fullConditionFailingTaskIds()
			.stream()
			.filter((id) -> !scores.gateFailingTaskIds().contains(id))
			.toList();
		return new Baseline(tasks.ids(), tasks.narrowedTaskIds(), PRIMARY_IS, scores.gateScore(),
				scores.gateFailingTaskIds(), HUMAN_LABEL_IS, scores.fullConditionScore(),
				scores.fullConditionFailingTaskIds(), diverging, modelCalls, headroom,
				verdict(tasks, scores, diverging, headroom));
	}

	/**
	 * The pass mark the next section's gate compares a candidate against.
	 * @return the primary baseline: the product's substring score
	 */
	public double passMarkForCandidates() {
		return this.baselineScore;
	}

	/**
	 * Whether the two scorers disagreed about this answer set.
	 * @return {@code true} when the human label rejected a task the primary baseline
	 * accepted
	 */
	public boolean armsDiverge() {
		return !this.divergingTaskIds.isEmpty();
	}

	private static String verdict(FrozenTaskSet tasks, ScoredAnswers scores, List<String> diverging, boolean headroom) {
		int failed = scores.gateFailingTaskIds().size();
		StringBuilder verdict = new StringBuilder();
		if (headroom) {
			verdict.append(failed)
				.append(" of ")
				.append(tasks.ids().size())
				.append(" frozen tasks fail the primary baseline, so the outer loop has room to move. Expected")
				.append(" baseline was ")
				.append(AUTHORED_EXPECTATION)
				.append(".");
		}
		else {
			verdict.append("STOP — the outer loop cannot run. The unmodified process passes every frozen task under")
				.append(" the primary substring score, and the gate's comparison is strict, so no candidate can beat")
				.append(" a baseline already at the ceiling.");
		}
		if (!diverging.isEmpty()) {
			verdict.append(" DIVERGENCE — the two scorers disagree on this run: ")
				.append(diverging.size())
				.append(" task(s) pass the primary baseline and fail the human label (")
				.append(diverging)
				.append("). The primary baseline is the weaker of the two there, and it is the one the loop is gated")
				.append(" by, by the ruling that the loop must optimise the scorer the product actually has.")
				.append(" The human label scored ")
				.append(scores.fullConditionScore())
				.append(" against the primary ")
				.append(scores.gateScore())
				.append(".");
		}
		else {
			verdict.append(" The two scorers agreed on every task this run, though all ")
				.append(tasks.narrowedTaskIds().size())
				.append(" task(s) carry conditions only the human label can express, so the agreement is this")
				.append(" answer set's and not the scorers'.");
		}
		return verdict.toString();
	}

}
