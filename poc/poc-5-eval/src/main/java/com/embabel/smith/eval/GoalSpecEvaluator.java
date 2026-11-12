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
