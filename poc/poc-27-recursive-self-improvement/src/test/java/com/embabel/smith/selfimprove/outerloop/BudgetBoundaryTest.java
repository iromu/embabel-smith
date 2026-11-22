/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.outerloop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.embabel.smith.selfimprove.control.HeldForApproval;
import com.embabel.smith.selfimprove.innerloop.ModelCallMeter;
import com.embabel.smith.selfimprove.innerloop.PolicyGenerator.MutationOperator;
import com.embabel.smith.selfimprove.innerloop.PolicyPrompt;
import com.embabel.smith.selfimprove.referee.FrozenTaskSet;
import com.embabel.smith.selfimprove.referee.Referee;

/**
 * Task 7.6: the model-call and generation budget is declared before the first call, a run
 * that exhausts it stops at the boundary, and the report says which measurements were not
 * taken.
 *
 * <p>
 * Offline; see {@link OuterLoopFixtures} for what the scores here do and do not mean.
 *
 * @author Ivan Rodriguez
 */
class BudgetBoundaryTest {

	private static final List<MutationOperator> OPERATORS = PolicyImprover.SCORED_MUTATIONS;

	private static final String T01 = "b27-t01";

	private static final String T02 = "b27-t02";

	@TempDir
	Path work;

	private final ModelCallMeter meter = new ModelCallMeter();

	@Test
	void aRunThatRunsOutOfCallsStopsAtTheBoundaryAndNamesWhatItDidNotMeasure() {
		FrozenTaskSet tasks = OuterLoopFixtures.frozenTasks();
		int cost = Budget.costPerCandidate(tasks.ids().size());
		// Room for exactly two of the five variants generation 1 proposes, and for a
		// second
		// generation the run never reaches.
		OuterRun run = runWith(Budget.declare(cost * 2, 2));

		assertThat(run.archive().size()).isEqualTo(2);
		assertThat(run.generations()).hasSize(1);
		// The ceiling is bought in reserve units (one deploy plus one invocation per
		// task),
		// while the meter only sees invocations: two candidates spend 12 calls against
		// the
		// 14 they were charged, and the difference is the deploys the referee replays for
		// free.
		assertThat(run.modelCalls()).isEqualTo(tasks.ids().size() * 2);
		assertThat(run.modelCalls()).isLessThanOrEqualTo(run.budget().maxModelCalls());
		assertThat(run.stopReason()).isEqualTo(OuterLoop.MODEL_CALL_BUDGET_SPENT);
		assertThat(run.notMeasured()).hasSizeGreaterThanOrEqualTo(4);
		assertThat(run.notMeasured()).anyMatch((gap) -> gap.contains("was not scored"));
		assertThat(run.notMeasured()).anyMatch((gap) -> gap.contains("generation 2 onward was not run"));
		assertThat(run.outcome().shape()).isEqualTo(RunOutcome.HILL_CLIMBING);
		assertThat(run.describe()).contains("declared budget").contains("not measured:");
	}

	@Test
	void aBudgetThatCannotBuyASingleCandidateSpendsNothingAndScoresNothing() {
		FrozenTaskSet tasks = OuterLoopFixtures.frozenTasks();
		OuterRun run = runWith(Budget.declare(tasks.ids().size() - 1, 3));

		assertThat(this.meter.count()).isZero();
		assertThat(run.archive().all()).isEmpty();
		assertThat(run.generations()).isEmpty();
		assertThat(run.stopReason()).isEqualTo(OuterLoop.MODEL_CALL_BUDGET_SPENT);
		assertThat(run.outcome().shape()).isEqualTo(RunOutcome.NOTHING_SCORED);
		assertThat(run.notMeasured()).allMatch((gap) -> gap.contains("was not scored"));
	}

	@Test
	void aRunThatUsesEveryGenerationSaysSoRatherThanLookingFinished() {
		FrozenTaskSet tasks = OuterLoopFixtures.frozenTasks();
		OuterRun run = runWith(Budget.declare(tasks.ids().size() * 20, 1));

		assertThat(run.stopReason()).isEqualTo(OuterLoop.GENERATION_BUDGET_SPENT);
		assertThat(run.generations()).hasSize(1);
		assertThat(run.outcome().completedGenerations()).isEqualTo(1);
	}

	@Test
	void aBudgetWithNothingInItIsRefusedRatherThanRun() {
		assertThatThrownBy(() -> Budget.declare(0, 2)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Budget.declare(60, 0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> NoiseFloor.injected(1.0, "test")).isInstanceOf(IllegalArgumentException.class);
	}

	private OuterRun runWith(Budget budget) {
		FrozenTaskSet tasks = OuterLoopFixtures.frozenTasks();
		PolicyImprover improver = PolicyImprover.surviving(PolicyPrompt.frozenSeed());
		Map<String, String[]> passing = Map.of("SEED", new String[] { T01, T02 }, "KEYWORD_INSTEAD_OF_PREFIX",
				new String[] { T01 }, "DROP_EXCLUSION", new String[] { T01 }, "DROP_TRIGGER", new String[] { T01 },
				"ADD_LATENCY_TRIGGER", new String[] {});
		Referee referee = OuterLoopFixtures.referee(tasks, OuterLoopFixtures.tableOf(improver, OPERATORS, passing),
				this.meter);
		return new OuterLoop(referee, this.meter, new HeldForApproval()).run(new OuterLoop.Request(improver, OPERATORS,
				Baseline.injected(OuterLoopFixtures.scoreOf(), "a score this test chooses, not the measured baseline"),
				NoiseFloor.injected(0.05, "a threshold this test chooses, not the measured artifact"), budget,
				this.work));
	}

}
