/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.baseline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.embabel.smith.builder.core.SpecPromptCaller;
import com.embabel.smith.selfimprove.innerloop.ModelCallMeter;
import com.embabel.smith.selfimprove.referee.FrozenTask;
import com.embabel.smith.selfimprove.referee.FrozenTaskSet;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Task 2.4 offline: the baseline records the unmodified process's score and the headroom
 * decision, and the headroom check is proven to be able to say "no".
 *
 * <p>
 * No model is called. The answers come from {@link BaselineFixtures} through the same
 * {@link SpecPromptCaller} seam the live run uses, and the call count is read off
 * {@link ModelCallMeter} rather than assumed, so what the offline run proves is that the
 * code asks each frozen prompt exactly once per pass and nothing else.
 *
 * @author Ivan Rodriguez
 */
class BaselineHeadroomTest {

	private static final FrozenTaskSet TASKS = BaselineFixtures.frozen();

	@Test
	void theFixtureAnswersTheCheckedInTasksAndNotSomeOtherSet() {
		// Both tables have to cover the six identifiers that are actually frozen, or a
		// renamed task becomes an empty answer and the baseline below measures a fixture
		// that no longer corresponds to the benchmark.
		assertThat(BaselineFixtures.PRIORS.keySet()).containsExactlyInAnyOrderElementsOf(TASKS.ids());
		assertThat(BaselineFixtures.CORRECT.keySet()).containsExactlyInAnyOrderElementsOf(TASKS.ids());
	}

	@Test
	void theUnmodifiedProcessLeavesHeadroomAndSaysHowMuch() {
		Baseline baseline = baselineWith(BaselineFixtures.PRIORS);

		assertThat(baseline.baselineFailingTaskIds()).containsExactly("b27-t01", "b27-t02", "b27-t03", "b27-t06");
		assertThat(baseline.baselineScore()).isCloseTo(2.0 / 6.0, within(1e-9));
		assertThat(baseline.humanLabelScore()).isCloseTo(2.0 / 6.0, within(1e-9));
		assertThat(baseline.divergingTaskIds()).as("both scorers agreed on every task here").isEmpty();
		assertThat(baseline.headroom()).as(baseline.verdict()).isTrue();
		assertThat(baseline.verdict()).contains("4 of 6").contains("room to move");
		assertThat(baseline.passMarkForCandidates()).isEqualTo(baseline.baselineScore());
	}

	@Test
	void anAllCorrectAnswerSetReportsNoHeadroomAndTellsTheLoopToStop() {
		// The non-vacuity case. A headroom check that has only ever seen 2/6 has not been
		// shown to be a check, so the ceiling answer set has to take the stop branch.
		Baseline baseline = baselineWith(BaselineFixtures.CORRECT);

		assertThat(baseline.baselineScore()).isCloseTo(1.0, within(1e-9));
		assertThat(baseline.humanLabelScore()).isCloseTo(1.0, within(1e-9));
		assertThat(baseline.headroom()).isFalse();
		assertThat(baseline.verdict()).contains("STOP").contains("the outer loop cannot run");
	}

	@Test
	void thePrimaryBaselineIsTheSubstringScoreAndTheHumanLabelIsRecordedAsTheGap() {
		// Every frozen task carries an anyOf or forbid clause, so the substring scorer is
		// the looser of the two and the only possible divergence is a narrowed task it
		// passes and the human label fails. That is the case the ruling accepts
		// knowingly:
		// the primary baseline is weaker there, and it stays primary because the loop has
		// to
		// optimise the scorer the product actually has. The record has to show the gap
		// rather than smooth it, so this asserts the divergence is named.
		Map<String, String> answers = new LinkedHashMap<>(BaselineFixtures.CORRECT);
		answers.put("b27-t04", BaselineFixtures.GATE_PASSES_BUT_THE_FULL_CHECK_DOES);

		Baseline baseline = baselineWith(answers);

		assertThat(baseline.baselineScore()).as("the substring scorer passes the narrowed task")
			.isCloseTo(1.0, within(1e-9));
		assertThat(baseline.humanLabelFailingTaskIds()).containsExactly("b27-t04");
		assertThat(baseline.humanLabelScore()).isLessThan(baseline.baselineScore());
		assertThat(baseline.divergingTaskIds()).containsExactly("b27-t04");
		assertThat(baseline.armsDiverge()).isTrue();
		assertThat(baseline.headroom()).as("the primary baseline has nothing left to beat").isFalse();
		assertThat(baseline.narrowedTaskIds()).isNotEmpty();
		assertThat(baseline.primaryBaselineIs()).as("the file says which field the gate and 7.4 read")
			.contains("EvalScorer")
			.contains("7.4");
		assertThat(baseline.humanLabelIs()).as("the file says the second number is the gap, not the pass mark")
			.contains("known gap")
			.contains("not as the pass mark");
		assertThat(baseline.verdict()).as("the divergence is stated, not footnoted")
			.contains("DIVERGENCE")
			.contains("b27-t04");
	}

	@Test
	void theFrozenPromptsReachTheModelVerbatimAndWithNothingInFrontOfThem() {
		List<String> systems = new ArrayList<>();
		List<String> prompts = new ArrayList<>();

		new BaselineRun(TASKS, BaselineFixtures.canned(TASKS, Map.of(), systems, prompts), new ModelCallMeter())
			.measure();

		assertThat(systems).as("an improvement prompt in front of the questions measures a modified process")
			.containsOnlyNulls();
		assertThat(prompts).containsExactlyElementsOf(TASKS.tasks().stream().map(FrozenTask::prompt).toList());
	}

	@Test
	void oneMeasurementAsksEveryFrozenPromptExactlyOnce() {
		ModelCallMeter meter = new ModelCallMeter();

		new BaselineRun(TASKS, BaselineFixtures.canned(TASKS, BaselineFixtures.PRIORS), meter).measure();

		// Measured, not read off the source: a second call per prompt would double the
		// paid
		// run and could report two different scores for one answer set.
		assertThat(meter.count()).isEqualTo(TASKS.ids().size());
	}

	private static Baseline baselineWith(Map<String, String> answers) {
		ModelCallMeter meter = new ModelCallMeter();
		ScoredAnswers scores = new BaselineRun(TASKS, BaselineFixtures.canned(TASKS, answers), meter).measure();
		return Baseline.of(TASKS, scores, meter.count());
	}

}
