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

package com.embabel.smith.skillgen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates an {@link EvalRunner} run set into the skill-creator benchmark: the pass rate for
 * the with-skill configuration, the pass rate for the no-skill baseline, and the delta — the
 * measured value of the generated skill. Pass rate is the fraction of all expectations passed
 * across the suite's evals for that configuration.
 *
 * @author Ivan Rodriguez
 */
public final class Benchmark {

	private Benchmark() {
	}

	/**
	 * Aggregates the runs (two per eval: with-skill and without-skill).
	 * @param runs the runs to aggregate.
	 * @return the summary with per-configuration pass rates, the delta, and per-eval detail.
	 */
	public static Summary aggregate(List<EvalRunner.EvalRun> runs) {
		int withPassed = 0;
		int withTotal = 0;
		int withoutPassed = 0;
		int withoutTotal = 0;
		Map<Integer, int[]> perEval = new LinkedHashMap<>();
		for (EvalRunner.EvalRun run : runs) {
			int id = run.eval().id();
			int[] cell = perEval.computeIfAbsent(id, (k) -> new int[4]);
			if ("with_skill".equals(run.config())) {
				cell[0] += run.grading().passed();
				cell[1] += run.grading().total();
				withPassed += run.grading().passed();
				withTotal += run.grading().total();
			}
			else {
				cell[2] += run.grading().passed();
				cell[3] += run.grading().total();
				withoutPassed += run.grading().passed();
				withoutTotal += run.grading().total();
			}
		}
		List<EvalScore> scores = new ArrayList<>();
		for (Map.Entry<Integer, int[]> e : perEval.entrySet()) {
			int[] c = e.getValue();
			String prompt = runs.stream()
				.filter((r) -> r.eval().id() == e.getKey())
				.map((r) -> r.eval().prompt())
				.findFirst()
				.orElse("");
			scores.add(new EvalScore(e.getKey(), prompt, c[0], c[1], c[2], c[3]));
		}
		double withRate = rate(withPassed, withTotal);
		double withoutRate = rate(withoutPassed, withoutTotal);
		return new Summary(withRate, withoutRate, withRate - withoutRate, scores);
	}

	private static double rate(int passed, int total) {
		return (total == 0) ? 0.0 : (double) passed / total;
	}

	/**
	 * The benchmark summary for one arm.
	 * @param withPassRate the with-skill pass rate.
	 * @param withoutPassRate the no-skill baseline pass rate.
	 * @param delta with-skill minus baseline — the skill's measured lift.
	 * @param perEval the per-eval detail.
	 */
	public record Summary(double withPassRate, double withoutPassRate, double delta, List<EvalScore> perEval) {
	}

	/**
	 * Per-eval pass counts for both configurations.
	 * @param evalId the eval id.
	 * @param prompt the scenario prompt.
	 * @param withPassed expectations passed with the skill.
	 * @param withTotal expectations graded with the skill.
	 * @param withoutPassed expectations passed without the skill.
	 * @param withoutTotal expectations graded without the skill.
	 */
	public record EvalScore(int evalId, String prompt, int withPassed, int withTotal, int withoutPassed,
			int withoutTotal) {
	}

}
