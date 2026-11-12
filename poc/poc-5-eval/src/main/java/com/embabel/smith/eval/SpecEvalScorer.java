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
import com.embabel.agent.eval.support.ResponseTimes;
import com.embabel.agent.eval.support.Score;
import com.embabel.agent.eval.support.SubjectiveScores;

/**
 * Deterministic, offline scoring of a goal-type spec's outputs, expressed in the eval
 * module's own {@link Score}/{@link FullScores} types so the composite is comparable to
 * the conversational eval.
 *
 * <p>
 * The conversational-only dimensions (subjective tone, response times) do not apply to a
 * single-shot goal run, so they are zeroed; the composite is the mean of the per-task
 * assertion scores.
 *
 * @author Ivan Rodriguez
 */
public class SpecEvalScorer {

	/**
	 * Score one task: {@code 1.0} when the goal output is present and satisfies the
	 * acceptance check, {@code 0.0} otherwise.
	 * @param goalOutput the spec's goal output for the task's input
	 * @param task the eval task to score
	 * @return the per-task assertion score
	 */
	public Score scoreOne(String goalOutput, SpecEvalTask task) {
		boolean pass = goalOutput != null && !goalOutput.isBlank() && task.acceptance().test(goalOutput);
		return new Score(task.input(), pass ? 1.0 : 0.0);
	}

	/**
	 * Composite score across all tasks: the mean of the per-task assertion scores.
	 * @param assertionScores the per-task assertion scores
	 * @return the composite scores across all tasks
	 */
	public FullScores composite(List<Score> assertionScores) {
		int failures = (int) assertionScores.stream().filter((s) -> s.getScore() < 1.0).count();
		double total = assertionScores.isEmpty() ? 0.0
				: assertionScores.stream().mapToDouble(Score::getScore).average().orElse(0.0);
		return new FullScores(failures, new SubjectiveScores(0.0, List.of()), assertionScores,
				new ResponseTimes(0, 0, 0), total);
	}

}
