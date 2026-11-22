/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.referee;

import java.util.List;

import com.embabel.smith.builder.eval.EvalScorer;
import com.embabel.smith.builder.eval.EvalTask;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 3.1: the referee is the product's own gate over the frozen task set, and offline
 * it separates a known-good candidate from a known-bad one. No endpoint and no model call
 * — the candidate answers come from a fixed table, so a failure here is about the referee
 * or the task set, not about a model.
 *
 * <p>
 * The baseline these tests compare against is the known-bad fixture's measured score, not
 * the recorded one. A recorded number now exists — task 2.4 measured 0.667 on the gate
 * arm — but reading it here would test the wrong thing: these are offline tests over
 * typed-in answers, and borrowing the live figure would make a fixture's verdict depend
 * on what a model happened to answer. Using the fixture's own measurement also exercises
 * the gate's strict comparison, because the bad candidate then has to beat its own score
 * to pass and does not.
 *
 * @author Ivan Rodriguez
 */
class FrozenGateVerdictsTest {

	private final FrozenTaskSet frozen = RefereeFixtures.frozenTasks();

	@Test
	void knownGoodCandidateClearsTheGateAndTheFrozenSet() {
		double baseline = this.frozen.score(RefereeFixtures.priorAnswers(this.frozen));
		Referee referee = Referee.start(RefereeFixtures.resources(), null,
				new RecordingInvokerFactory(this.frozen, RefereeFixtures.CORRECT));

		RefereeReport report = referee.score(RefereeFixtures.candidate("known-good"), baseline);

		assertThat(report.gated()).isTrue();
		assertThat(report.gateStage()).isNull();
		assertThat(report.gateScore()).isEqualTo(1.0);
		assertThat(report.frozenScore()).isEqualTo(1.0);
		assertThat(report.frozenFailedTaskIds()).isEmpty();
		assertThat(report.scoredTaskIds()).isEqualTo(this.frozen.ids());
	}

	@Test
	void knownBadCandidateIsRejectedAtTheBaselineAndNamesTheTasksItMissed() {
		double baseline = this.frozen.score(RefereeFixtures.priorAnswers(this.frozen));
		Referee referee = Referee.start(RefereeFixtures.resources(), null,
				new RecordingInvokerFactory(this.frozen, RefereeFixtures.PRIORS));

		RefereeReport report = referee.score(RefereeFixtures.candidate("known-bad"), baseline);

		assertThat(report.gated()).isFalse();
		// Not validation, not security: the pack is fine, the answers are the priors. A
		// rejection
		// at either earlier stage would mean the fixture, not the task set, decided the
		// verdict.
		assertThat(report.gateStage()).isEqualTo("baseline");
		assertThat(report.frozenFailedTaskIds()).containsExactly("b27-t01", "b27-t02", "b27-t03", "b27-t06");
		assertThat(report.frozenScore()).isEqualTo(baseline);
		// The two scorers agreed on this candidate, so the verdict was not driven by a
		// task the
		// frozen check and the gate's substring check scored differently.
		assertThat(report.gateScore()).isEqualTo(report.frozenScore());
	}

	@Test
	void theFrozenSetScoresThePriorsWhereTheReadmeExpectsThem() {
		// The task set's README records an expected baseline of 2/6 as design intent.
		// This asserts the
		// fixture answers land there, so the headroom the outer loop is budgeted against
		// is real
		// rather than assumed; it is not a substitute for task 2.4's measurement of the
		// process.
		assertThat(this.frozen.failedTaskIds(RefereeFixtures.priorAnswers(this.frozen))).hasSize(4);
		assertThat(this.frozen.score(RefereeFixtures.correctAnswers(this.frozen))).isEqualTo(1.0);
	}

	@Test
	void eachCandidateIsAskedEachPromptOnceAcrossBothScorers() {
		// The gate runs the pack itself during re-eval. Without the recording wrapper the
		// referee
		// would ask the same six prompts twice — double the eval spend, and two scores
		// that could
		// disagree for no reason but model noise.
		RecordingInvokerFactory factory = new RecordingInvokerFactory(this.frozen, RefereeFixtures.CORRECT);
		Referee referee = Referee.start(RefereeFixtures.resources(), null, factory);

		referee.score(RefereeFixtures.candidate("known-good"), 0.0);

		assertThat(factory.asked()).hasSize(this.frozen.tasks().size());
	}

	@Test
	void noTaskCanBePassedByEchoingItsOwnQuestion() {
		// The invariant benchmark/tasks/README.md states, checked over the checked-in set
		// before
		// anything is scored: a question containing its own answer would inflate the
		// baseline.
		this.frozen.tasks().forEach((task) -> assertThat(task.restatedPhrases()).as("task " + task.id()).isEmpty());

		this.frozen.assertNoRestatedPhrases();
	}

	@Test
	void theGateCannotCarryAForbidConditionWhichIsWhyTheReportKeepsTwoScores() {
		// Why the referee reports a frozen score next to the gate's. EvalScorer tests one
		// substring,
		// so an answer that trips a forbidden phrase still scores full marks through the
		// gate. This
		// is the measured limit of the conversion, not a hypothetical one.
		FrozenTask strict = this.frozen.tasks()
			.stream()
			.filter((task) -> task.id().equals("b27-t04"))
			.findFirst()
			.orElseThrow();
		String answer = "No — equal is not progress, yes, the gate is strict.";

		assertThat(strict.passes(answer)).isFalse();
		assertThat(this.frozen.narrowedTaskIds()).contains("b27-t04");
		assertThat(EvalScorer.score(List.of(new EvalTask(strict.id(), strict.prompt(), strict.require().get(0))),
				List.of(answer)))
			.isEqualTo(1.0);
	}

}
