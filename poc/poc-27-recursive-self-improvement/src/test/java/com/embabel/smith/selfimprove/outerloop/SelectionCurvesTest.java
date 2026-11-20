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
import com.embabel.smith.selfimprove.referee.FrozenTaskSet;
import com.embabel.smith.selfimprove.referee.Referee;

/**
 * Task 7.5: archive selection and greedy selection are compared over the identical
 * generations, and both curves are reported.
 *
 * <p>
 * Offline; see {@link OuterLoopFixtures} for what the scores here do and do not mean.
 *
 * @author Ivan Rodriguez
 */
class SelectionCurvesTest {

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
	void aRunReportsBothCurvesOverItsOwnGenerations() {
		FrozenTaskSet tasks = OuterLoopFixtures.frozenTasks();
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
		Referee referee = OuterLoopFixtures.referee(tasks, rows, this.meter);

		OuterRun run = new OuterLoop(referee, this.meter, new HeldForApproval()).run(new OuterLoop.Request(gen1,
				OPERATORS, Baseline.injected(OuterLoopFixtures.scoreOf(), "task 2.4 has not recorded a baseline"),
				NoiseFloor.injected(0.05, "task 2.5 has not measured a noise floor"), Budget.declare(200, 2),
				this.work));

		SelectionCurves.Curves curves = run.curves();
		assertThat(curves.archive()).hasSize(2);
		assertThat(curves.greedy()).hasSize(curves.archive().size());
		assertThat(run.describe()).contains("archive:").contains("greedy:");
	}

	@Test
	void greedySelectionCannotReachAWinnerThatDescendsFromALoser() {
		// Built by hand rather than run, because the case only arises when the best
		// variant
		// of the later generation is the child of a variant greedy already discarded. A
		// run
		// that selects from its own archive gets there; greedy, which may only extend its
		// current champion, cannot see that branch at all.
		List<VariantRecord> records = List.of(record(1, "b27-a", null, 0.5), record(1, "b27-b", null, 0.6),
				record(2, "b27-c", "b27-a", 0.9), record(2, "b27-d", "b27-b", 0.7));

		SelectionCurves.Curves curves = SelectionCurves.over(records);

		assertThat(curves.archive()).extracting(SelectionCurves.Point::variantId).containsExactly("b27-b", "b27-c");
		assertThat(curves.greedy()).extracting(SelectionCurves.Point::variantId).containsExactly("b27-b", "b27-d");
		assertThat(curves.archive().get(1).score()).isEqualTo(0.9);
		assertThat(curves.greedy().get(1).score()).isEqualTo(0.7);
		assertThat(curves.divergedAtGeneration()).isEqualTo(2);
		assertThat(curves.describe()).contains("archive:").contains("greedy:").contains("generation 2");
	}

	@Test
	void bothRulesAgreeWhenTheBestVariantIsReachableFromTheChampion() {
		List<VariantRecord> records = List.of(record(1, "b27-a", null, 0.6), record(1, "b27-b", null, 0.5),
				record(2, "b27-c", "b27-a", 0.9));

		SelectionCurves.Curves curves = SelectionCurves.over(records);

		assertThat(curves.divergedAtGeneration()).isNull();
		assertThat(curves.archive()).isEqualTo(curves.greedy());
		assertThat(curves.describe()).contains("does not separate them");
	}

	private static VariantRecord record(int generation, String variantId, String parentId, double score) {
		return new VariantRecord(generation, variantId, parentId, "b27-improver", "SEED", true, "baseline", score,
				score, Improvement.paired(0.0, score, NoiseFloor.injected(0.05, "test"), variantId, parentId), 6, 1L);
	}

}
