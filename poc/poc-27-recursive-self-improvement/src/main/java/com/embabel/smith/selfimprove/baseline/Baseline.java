/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.baseline;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
 * <p>
 * <b>A task the two arms disagree about does not count in the deciding number.</b>
 * {@link #decidingScore()} scores only {@link #decidingTaskIds()} — the tasks both arms
 * agreed on — and {@link #excludedTaskIds()} names the rest, so the smaller denominator
 * sits in the record instead of being folded into a score that one arm decided. On the
 * run recorded in {@code evidence/baseline.json} that removes 2 of 6 tasks: the deciding
 * score is 0.5 over 4 where the primary arm alone reads 0.667 over 6. That is a smaller
 * denominator, not a better result, and anything compared against it has to be scored on
 * the same four tasks. The exclusion is derived from the two verdict lists this record
 * already carries and is never accepted from a caller: the compact constructor recomputes
 * it and refuses a record whose excluded list is anything other than the disagreement,
 * which is the whole answer to excluding a task because it is inconvenient rather than
 * because the arms split on it.
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
 * @param excludedTaskIds the tasks the deciding score does not count: exactly the tasks
 * the two arms disagree about, in either direction, derived from the two verdict lists
 * above. It contains {@code divergingTaskIds} and is equal to it whenever the label is
 * the stricter arm, which it is on this task set.
 * @param decidingTaskIds the tasks the deciding score does count: {@code taskIds} minus
 * {@code excludedTaskIds}, in task order
 * @param decidingScore {@code decidingTaskIds} under the primary arm — the number
 * {@link #passMarkForCandidates()} returns, and equal to {@code baselineScore} when
 * nothing is excluded. Null when the arms disagree about every task, which leaves no
 * denominator to score; it is not replaced by zero.
 * @param modelCalls the calls this measurement spent, read off the meter
 * @param headroom whether the primary baseline leaves anything to improve
 * @param verdict what the run concluded, in the file, including the stop instruction when
 * there is no headroom, the divergence statement when the two scores disagree, and the
 * deciding denominator with the ids excluded to reach it
 * @author Ivan Rodriguez
 */
public record Baseline(List<String> taskIds, List<String> narrowedTaskIds, String primaryBaselineIs,
		double baselineScore, List<String> baselineFailingTaskIds, String humanLabelIs, double humanLabelScore,
		List<String> humanLabelFailingTaskIds, List<String> divergingTaskIds, List<String> excludedTaskIds,
		List<String> decidingTaskIds, Double decidingScore, int modelCalls, boolean headroom, String verdict) {

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
		excludedTaskIds = List.copyOf(excludedTaskIds);
		decidingTaskIds = List.copyOf(decidingTaskIds);
		assertExclusionIsTheDisagreement(taskIds, baselineFailingTaskIds, humanLabelFailingTaskIds, excludedTaskIds,
				decidingTaskIds, decidingScore);
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
		List<String> excluded = disagreeingTaskIds(tasks.ids(), scores.gateFailingTaskIds(),
				scores.fullConditionFailingTaskIds());
		List<String> deciding = remainingTaskIds(tasks.ids(), excluded);
		return new Baseline(tasks.ids(), tasks.narrowedTaskIds(), PRIMARY_IS, scores.gateScore(),
				scores.gateFailingTaskIds(), HUMAN_LABEL_IS, scores.fullConditionScore(),
				scores.fullConditionFailingTaskIds(), diverging, excluded, deciding,
				scoreOverAgreeing(deciding, scores.gateFailingTaskIds()), modelCalls, headroom,
				verdict(tasks, scores, diverging, excluded, deciding, headroom));
	}

	/**
	 * The pass mark the next section's gate compares a candidate against: the primary
	 * score over the tasks both arms agreed the answer set deserved, with every task the
	 * two arms split on left out.
	 *
	 * <p>
	 * <b>The comparison has to be unit-matched.</b> This is a pass-rate over
	 * {@link #decidingTaskIds()}, not over {@link #taskIds()}, so a candidate compared
	 * against it must be scored on those same tasks — the gate's own re-eval scores all
	 * six and comparing it to this number would turn an exclusion into a free delta. When
	 * nothing is excluded the two numbers are the same and the gate's comparison is
	 * unchanged.
	 * @return the deciding score
	 * @throws IllegalStateException when the two arms disagree about every task, because
	 * then no task is left whose verdict both arms vouch for and there is no pass mark to
	 * compare against
	 */
	public double passMarkForCandidates() {
		if (this.decidingScore == null) {
			throw new IllegalStateException("there is no deciding score: the two arms disagree about all "
					+ this.taskIds.size() + " frozen tasks (" + this.excludedTaskIds
					+ "), so every candidate would be judged on a denominator of zero. Narrow the task set or"
					+ " reconcile the two arms before comparing anything.");
		}
		return this.decidingScore.doubleValue();
	}

	/**
	 * Whether the two scorers disagreed about this answer set.
	 * @return {@code true} when the human label rejected a task the primary baseline
	 * accepted
	 */
	public boolean armsDiverge() {
		return !this.divergingTaskIds.isEmpty();
	}

	/**
	 * The tasks the two arms disagree about, in task order, in either direction: the
	 * label rejected it and the primary accepted it — the direction
	 * {@code divergingTaskIds} already records — or the primary rejected it and the label
	 * accepted it. Both arms pass or both fail every task not named here, which is what
	 * makes the score over the rest independent of which arm computed it.
	 * @param ids the frozen task identifiers, in task order
	 * @param gateFailing the tasks the substring arm rejected
	 * @param labelFailing the tasks the human label rejected
	 * @return the identifiers whose verdict differs between the two arms
	 */
	private static List<String> disagreeingTaskIds(List<String> ids, List<String> gateFailing,
			List<String> labelFailing) {
		Set<String> gate = new HashSet<>(gateFailing);
		Set<String> label = new HashSet<>(labelFailing);
		List<String> disagreed = new ArrayList<>();
		for (String id : ids) {
			if (gate.contains(id) != label.contains(id)) {
				disagreed.add(id);
			}
		}
		return List.copyOf(disagreed);
	}

	private static List<String> remainingTaskIds(List<String> ids, List<String> excluded) {
		Set<String> dropped = new HashSet<>(excluded);
		return ids.stream().filter((id) -> !dropped.contains(id)).toList();
	}

	/**
	 * The deciding score: the primary arm's pass-rate over the tasks both arms agreed on.
	 * The label arm is not consulted because it does not need to be — a task survives the
	 * exclusion only when the two arms gave it the same verdict, so either arm scores the
	 * deciding set identically and the primary one is the arm the gate uses.
	 * @param deciding the tasks both arms agreed on, in task order
	 * @param gateFailing the tasks the substring arm rejected
	 * @return the pass-rate, or null when there is no task left to score
	 */
	private static Double scoreOverAgreeing(List<String> deciding, List<String> gateFailing) {
		if (deciding.isEmpty()) {
			return null;
		}
		Set<String> failed = new HashSet<>(gateFailing);
		long passed = deciding.stream().filter((id) -> !failed.contains(id)).count();
		return (double) passed / deciding.size();
	}

	/**
	 * Refuses a record whose deciding fields were not derived from the two verdict lists.
	 * This is why the exclusion cannot be steered: nothing in this class takes an
	 * exclusion list from a caller, and a hand-built record naming a task to drop that
	 * the two arms actually agreed on fails here rather than quietly shrinking the
	 * denominator.
	 */
	private static void assertExclusionIsTheDisagreement(List<String> ids, List<String> gateFailing,
			List<String> labelFailing, List<String> excluded, List<String> deciding, Double score) {
		List<String> expectedExcluded = disagreeingTaskIds(ids, gateFailing, labelFailing);
		List<String> expectedDeciding = remainingTaskIds(ids, expectedExcluded);
		Double expectedScore = scoreOverAgreeing(expectedDeciding, gateFailing);
		if (expectedExcluded.equals(excluded) && expectedDeciding.equals(deciding) && sameScore(expectedScore, score)) {
			return;
		}
		throw new IllegalStateException("the deciding fields must name the tasks the two arms disagree about, derived"
				+ " from the two verdict lists and not chosen: expected excluded " + expectedExcluded + ", deciding "
				+ expectedDeciding + ", score " + expectedScore + "; found " + excluded + ", " + deciding + ", " + score
				+ ". Excluding a task because it is inconvenient, rather than because the arms split on it, is the"
				+ " failure this record refuses to carry.");
	}

	private static boolean sameScore(Double left, Double right) {
		return Objects.equals(left, right);
	}

	private static String verdict(FrozenTaskSet tasks, ScoredAnswers scores, List<String> diverging,
			List<String> excluded, List<String> deciding, boolean headroom) {
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
		appendDeciding(verdict, tasks, scores, excluded, deciding);
		return verdict.toString();
	}

	private static void appendDeciding(StringBuilder verdict, FrozenTaskSet tasks, ScoredAnswers scores,
			List<String> excluded, List<String> deciding) {
		int total = tasks.ids().size();
		if (excluded.isEmpty()) {
			verdict.append(" DECIDING SCORE — no task is excluded, the two arms agreed on all ")
				.append(total)
				.append(", so the deciding score is the primary score over the whole set.");
			return;
		}
		Double score = scoreOverAgreeing(deciding, scores.gateFailingTaskIds());
		verdict.append(" DECIDING SCORE — ")
			.append(excluded.size())
			.append(" task(s) (")
			.append(excluded)
			.append(") are excluded because the two arms disagree about them, so the number that decides is ")
			.append((score != null) ? score : "undefined — the arms disagree about every task")
			.append(" over the remaining ")
			.append(deciding.size())
			.append(" of ")
			.append(total)
			.append(" tasks, not ")
			.append(scores.gateScore())
			.append(" over all ")
			.append(total)
			.append(". A smaller denominator is not a better score, and a candidate compared against this number has")
			.append(" to be scored on the same ")
			.append(deciding.size())
			.append(" tasks.");
	}

}
