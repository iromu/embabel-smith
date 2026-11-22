/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.baseline;

import java.util.List;

import com.embabel.smith.builder.eval.EvalScorer;
import com.embabel.smith.selfimprove.referee.FrozenTaskSet;

/**
 * One answer set scored twice, because the frozen set asks more of an answer than the
 * gate's scorer can carry.
 *
 * <p>
 * <b>{@link #gateScore()} is the primary number; {@link #fullConditionScore()} is the
 * human label.</b> Every task of the frozen set carries an {@code anyOf} or a
 * {@code forbid} condition ({@link FrozenTaskSet#narrowedTaskIds()} names them), and the
 * conversion into the gate's shape keeps only the first {@code require} phrase — so the
 * two can disagree, and a narrowed task the human label fails can pass the gate. Which
 * one is primary is a ruling, not a preference: the loop has to optimise the scorer the
 * product actually has, or the POC measures a system that does not exist.
 * {@link Baseline} records both, names the primary one, and lists the tasks where they
 * diverged.
 *
 * <p>
 * The gate arm is computed by calling {@code EvalScorer} — the same static call
 * {@code GateChain.reeval} makes — rather than by reimplementing its substring test here.
 * That is a read of a fixed artifact, not a mutation of it, and it is what keeps this
 * evidence from drifting from the scorer it is describing. It does not widen what is
 * reachable: the scorer's jar is already on this module's classpath because the gate
 * calls it, and the boundary the claim is relative to is the separate referee process
 * ({@code evidence/MUTABILITY.md}, {@code referee-process}), whose own package still
 * never names the scorer.
 *
 * @param gateScore the primary pass-rate: the product's single-substring scorer
 * @param gateFailingTaskIds the tasks that scorer rejected, in task order
 * @param fullConditionScore the human label: the pass-rate under each task file's full
 * recorded verdict
 * @param fullConditionFailingTaskIds the tasks that verdict rejected, in task order
 * @author Ivan Rodriguez
 */
public record ScoredAnswers(double gateScore, List<String> gateFailingTaskIds, double fullConditionScore,
		List<String> fullConditionFailingTaskIds) {

	public ScoredAnswers {
		gateFailingTaskIds = List.copyOf(gateFailingTaskIds);
		fullConditionFailingTaskIds = List.copyOf(fullConditionFailingTaskIds);
	}

	/**
	 * Scores one answer set on both arms.
	 * @param tasks the frozen set the answers answer
	 * @param answers one answer per task, in task order; a missing answer scores as a
	 * failure on both arms, never as a pass
	 * @return both numbers, with the tasks each arm rejected
	 */
	public static ScoredAnswers of(FrozenTaskSet tasks, List<String> answers) {
		var gateTasks = tasks.toEvalTasks();
		return new ScoredAnswers(EvalScorer.score(gateTasks, answers), EvalScorer.failingTasks(gateTasks, answers),
				tasks.score(answers), tasks.failedTaskIds(answers));
	}

	/**
	 * Whether the arm the gate scores with has anywhere left to go. The full-condition
	 * arm is deliberately not consulted: an improvement the gate cannot see cannot clear
	 * the gate, so headroom that only the second arm can measure is not headroom the
	 * outer loop can use.
	 * @return {@code true} when at least one frozen task fails the gate's arm
	 */
	public boolean gateArmHasHeadroom() {
		return !this.gateFailingTaskIds.isEmpty();
	}

}
