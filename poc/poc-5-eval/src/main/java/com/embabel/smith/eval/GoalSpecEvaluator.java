/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.eval;

import java.util.List;

import com.embabel.agent.eval.support.FullScores;
import com.embabel.agent.eval.support.Score;

/**
 * Runs a goal-type spec against a set of eval tasks and produces a composite score (task
 * 6.1).
 *
 * <p>
 * Decoupled from the invocation mechanism via {@link SpecInvoker}: the endpoint-gated
 * production path is {@link AgentInvocationSpecInvoker}, while offline tests inject a
 * fake invoker so the full evaluate → score → composite flow is verifiable without a live
 * model.
 *
 * @author Ivan Rodriguez
 */
public class GoalSpecEvaluator {

	private final SpecEvalScorer scorer = new SpecEvalScorer();

	public FullScores evaluate(SpecInvoker invoker, List<SpecEvalTask> tasks) {
		List<Score> scores = tasks.stream()
			.map((task) -> this.scorer.scoreOne(invoker.invoke(task.input()), task))
			.toList();
		return this.scorer.composite(scores);
	}

}
