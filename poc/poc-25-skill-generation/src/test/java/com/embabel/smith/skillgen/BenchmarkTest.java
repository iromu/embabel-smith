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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/**
 * Offline tests for the benchmark aggregation: pass rates per configuration and the skill lift
 * (the with-skill minus no-skill delta).
 *
 * @author Ivan Rodriguez
 */
class BenchmarkTest {

	@Test
	void computesPassRatesAndDelta() {
		List<EvalRunner.EvalRun> runs = List.of(run(1, "with_skill", 2, 3), run(1, "without_skill", 1, 3),
				run(2, "with_skill", 1, 2), run(2, "without_skill", 0, 2));
		Benchmark.Summary summary = Benchmark.aggregate(runs);
		// with: 3/5 = 0.6; without: 1/5 = 0.2; delta 0.4
		assertThat(summary.withPassRate()).isEqualTo(0.6);
		assertThat(summary.withoutPassRate()).isEqualTo(0.2);
		assertThat(summary.delta()).isCloseTo(0.4, Offset.offset(1e-9));
		assertThat(summary.perEval()).hasSize(2);
	}

	@Test
	void skillLiftIsZeroWhenBothMatch() {
		List<EvalRunner.EvalRun> runs = List.of(run(1, "with_skill", 2, 2), run(1, "without_skill", 2, 2));
		assertThat(Benchmark.aggregate(runs).delta()).isEqualTo(0.0);
	}

	@Test
	void emptyRunSetYieldsZeroRates() {
		Benchmark.Summary summary = Benchmark.aggregate(List.of());
		assertThat(summary.withPassRate()).isEqualTo(0.0);
		assertThat(summary.withoutPassRate()).isEqualTo(0.0);
	}

	private static EvalRunner.EvalRun run(int id, String config, int passed, int total) {
		List<SkillGrader.GradedExpectation> expectations = new ArrayList<>();
		for (int i = 0; i < total; i++) {
			expectations.add(new SkillGrader.GradedExpectation("e" + i, i < passed, "evidence"));
		}
		return new EvalRunner.EvalRun(new EvalSuite.Eval(id, "prompt", "expected", List.of(), List.of()), config,
				"answer", new SkillGrader.Grading(expectations));
	}

}
