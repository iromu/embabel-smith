/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.outerloop;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.embabel.smith.selfimprove.control.HeldForApproval;
import com.embabel.smith.selfimprove.innerloop.ModelCallMeter;
import com.embabel.smith.selfimprove.innerloop.PolicyGenerator.MutationOperator;
import com.embabel.smith.selfimprove.innerloop.PolicyPrompt;
import com.embabel.smith.selfimprove.referee.Referee;

/**
 * Task 7.7 — the honesty task of the section: a run with one completed generation is
 * reported as one round of hill-climbing with the recursive claim marked untested, and
 * the reporting path is covered rather than asserted in prose.
 *
 * <p>
 * A single generation has one improver, and nothing ever improved that improver. Calling
 * that recursive self-improvement is the failure this task exists to prevent, and calling
 * it a <em>failure</em> of the POC would be the same error in the other direction: the
 * claim is untested, and stays untested whatever the single generation scored.
 *
 * @author Ivan Rodriguez
 */
class SingleGenerationOutcomeTest {

	private static final List<MutationOperator> OPERATORS = PolicyImprover.SCORED_MUTATIONS;

	private static final String T01 = "b27-t01";

	private static final String T02 = "b27-t02";

	private static final String T03 = "b27-t03";

	private static final String T06 = "b27-t06";

	private static final String[] ALL = { T01, T02, T03, T06 };

	private static final String[] ONE = { T01 };

	@TempDir
	Path work;

	private final ModelCallMeter meter = new ModelCallMeter();

	@Test
	void oneCompletedGenerationIsOneRoundOfHillClimbingWithTheRecursiveClaimUntested() {
		OuterRun run = run(1);

		RunOutcome outcome = run.outcome();
		assertThat(outcome.completedGenerations()).isEqualTo(1);
		assertThat(outcome.shape()).isEqualTo(RunOutcome.HILL_CLIMBING);
		assertThat(outcome.recursiveClaim()).isEqualTo(RunOutcome.UNTESTED);
		assertThat(outcome.statement()).contains("hill-climbing").contains("untested");
		// The best variant of that one generation may well have beaten its baseline. The
		// report says so and still refuses to call the result recursive.
		assertThat(run.generations().get(0).improvement().verdict()).isEqualTo(Improvement.IMPROVEMENT);
		assertThat(run.describe()).contains(RunOutcome.HILL_CLIMBING).endsWith(outcome.statement());
	}

	@Test
	void aSecondGenerationIsWhatTurnsHillClimbingIntoASeriesAndStopsShortOfTheInterpreter() {
		// Control for the test above: same fixtures, one more generation allowed. The
		// only
		// thing that changes the outcome is the number of completed generations.
		OuterRun run = run(2);

		RunOutcome outcome = run.outcome();
		assertThat(outcome.completedGenerations()).isEqualTo(2);
		assertThat(outcome.shape()).isEqualTo(RunOutcome.DERIVATIVE_SERIES);
		assertThat(outcome.shape()).isNotEqualTo(RunOutcome.HILL_CLIMBING);
		assertThat(outcome.recursiveClaim()).isNotEqualTo(RunOutcome.UNTESTED);
		assertThat(outcome.statement()).doesNotContain("untested").contains("interpreter");
	}

	@Test
	void aRunThatScoredNothingClaimsNothing() {
		RunOutcome outcome = RunOutcome.of(List.of());

		assertThat(outcome.shape()).isEqualTo(RunOutcome.NOTHING_SCORED);
		assertThat(outcome.recursiveClaim()).isEqualTo(RunOutcome.UNTESTED);
		assertThat(outcome.completedGenerations()).isZero();
	}

	private OuterRun run(int generations) {
		PolicyImprover gen1 = PolicyImprover.surviving(PolicyPrompt.frozenSeed());
		PolicyImprover gen2 = gen1
			.derive(OuterLoopFixtures.proposalFor(gen1.propose(OPERATORS), MutationOperator.SEED));
		Map<String, String[]> gen1Passing = Map.of("SEED", ALL, "KEYWORD_INSTEAD_OF_PREFIX", ONE, "DROP_EXCLUSION", ONE,
				"DROP_TRIGGER", ONE, "ADD_LATENCY_TRIGGER", ONE);
		Map<String, String[]> gen2Passing = Map.of("SEED", ONE, "KEYWORD_INSTEAD_OF_PREFIX", ALL, "DROP_EXCLUSION", ONE,
				"DROP_TRIGGER", ONE, "ADD_LATENCY_TRIGGER", ONE);
		Map<String, Map<String, String>> rows = new LinkedHashMap<>(
				OuterLoopFixtures.tableOf(gen1, OPERATORS, gen1Passing));
		rows.putAll(OuterLoopFixtures.tableOf(gen2, OPERATORS, gen2Passing));
		Referee referee = OuterLoopFixtures.referee(OuterLoopFixtures.frozenTasks(), rows, this.meter);
		return new OuterLoop(referee, this.meter, new HeldForApproval()).run(new OuterLoop.Request(gen1, OPERATORS,
				Baseline.injected(OuterLoopFixtures.scoreOf(), "a score this test chooses, not the measured baseline"),
				NoiseFloor.injected(0.05, "a threshold this test chooses, not the measured artifact"),
				Budget.declare(OuterLoopFixtures.frozenTasks().ids().size() * 20, generations), this.work));
	}

}
