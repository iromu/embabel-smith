/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.skill;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline tests for the benchmark (task 2.2): the lift the publish gate compares against
 * its minimum is computed from canned gradings — the fraction of expectations passed in
 * each arm — and the per-eval detail a blocked build reports is carried alongside it.
 *
 * @author Ivan Rodriguez
 */
class BenchmarkTest {

	private static EvalSuite.Eval eval(int id, String prompt) {
		return new EvalSuite.Eval(id, prompt, "a correct result", List.of(), List.of("e1", "e2"));
	}

	private static EvalRunner.EvalRun run(EvalSuite.Eval eval, String config, int passed, int total) {
		List<SkillGrader.GradedExpectation> graded = new ArrayList<>();
		for (int i = 0; i < total; i++) {
			graded.add(new SkillGrader.GradedExpectation("e" + i, i < passed, "evidence"));
		}
		return new EvalRunner.EvalRun(eval, config, "answer", new SkillGrader.Grading(graded));
	}

	@Test
	void theLiftIsTheWithSkillPassRateMinusTheBaseline() {
		EvalSuite.Eval first = eval(1, "receive a chilled pallet");
		EvalSuite.Eval second = eval(2, "reject a temperature breach");
		List<EvalRunner.EvalRun> runs = List.of(run(first, EvalRunner.WITH_SKILL, 2, 2),
				run(first, EvalRunner.WITHOUT_SKILL, 1, 2), run(second, EvalRunner.WITH_SKILL, 1, 2),
				run(second, EvalRunner.WITHOUT_SKILL, 0, 2));

		Benchmark.Summary summary = Benchmark.aggregate(runs);

		// 3 of 4 expectations pass with the skill, 1 of 4 without it.
		assertThat(summary.withPassRate()).isEqualTo(0.75);
		assertThat(summary.withoutPassRate()).isEqualTo(0.25);
		assertThat(summary.lift()).isEqualTo(0.5);
	}

	@Test
	void aSkillThatDoesNotHelpHasZeroLift() {
		EvalSuite.Eval only = eval(1, "receive a chilled pallet");
		List<EvalRunner.EvalRun> runs = List.of(run(only, EvalRunner.WITH_SKILL, 1, 2),
				run(only, EvalRunner.WITHOUT_SKILL, 1, 2));

		assertThat(Benchmark.aggregate(runs).lift()).isZero();
	}

	@Test
	void aSkillThatConfusesTheAgentHasNegativeLift() {
		EvalSuite.Eval only = eval(1, "receive a chilled pallet");
		List<EvalRunner.EvalRun> runs = List.of(run(only, EvalRunner.WITH_SKILL, 0, 2),
				run(only, EvalRunner.WITHOUT_SKILL, 2, 2));

		assertThat(Benchmark.aggregate(runs).lift()).isEqualTo(-1.0);
	}

	@Test
	void eachEvalKeepsItsOwnCountsForBothArms() {
		EvalSuite.Eval first = eval(1, "receive a chilled pallet");
		EvalSuite.Eval second = eval(2, "reject a temperature breach");
		List<EvalRunner.EvalRun> runs = List.of(run(first, EvalRunner.WITH_SKILL, 2, 2),
				run(first, EvalRunner.WITHOUT_SKILL, 1, 2), run(second, EvalRunner.WITH_SKILL, 1, 2),
				run(second, EvalRunner.WITHOUT_SKILL, 0, 2));

		List<Benchmark.EvalScore> perEval = Benchmark.aggregate(runs).perEval();

		assertThat(perEval).hasSize(2);
		assertThat(perEval.get(0).evalId()).isEqualTo(1);
		assertThat(perEval.get(0).prompt()).isEqualTo("receive a chilled pallet");
		assertThat(perEval.get(0).withPassed()).isEqualTo(2);
		assertThat(perEval.get(0).withTotal()).isEqualTo(2);
		assertThat(perEval.get(0).withoutPassed()).isEqualTo(1);
		assertThat(perEval.get(0).withoutTotal()).isEqualTo(2);
		assertThat(perEval.get(1).evalId()).isEqualTo(2);
		assertThat(perEval.get(1).withPassed()).isEqualTo(1);
		assertThat(perEval.get(1).withoutPassed()).isZero();
	}

	@Test
	void noRunsAtAllReportsNoLiftRatherThanDividingByZero() {
		Benchmark.Summary summary = Benchmark.aggregate(List.of());

		assertThat(summary.withPassRate()).isZero();
		assertThat(summary.withoutPassRate()).isZero();
		assertThat(summary.lift()).isZero();
		assertThat(summary.perEval()).isEmpty();
	}

}
