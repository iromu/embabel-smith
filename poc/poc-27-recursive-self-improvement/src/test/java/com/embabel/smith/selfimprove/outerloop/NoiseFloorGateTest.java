/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.outerloop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

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
 * Task 7.4: improvement is a paired delta judged against the noise floor, and a delta
 * inside the floor is both reported as no improvement and refused by the gate.
 *
 * <p>
 * The floor is injected as a value throughout, not read from
 * {@code evidence/noise-floor.json}. Two reasons, and the second only arrived with the
 * measurement. This test has to vary the floor, because its whole claim is that one delta
 * is refused at a floor of 0.2 and accepted at 0.05 — an artifact holding a single value
 * cannot express that, and reading one here would leave the class asserting a threshold
 * rather than the comparison. And the measured floor, now that 2.5 has produced it, is
 * 0.0, so nothing on disk supplies the pair this test needs. Neither reason licenses
 * manufacturing the artifact, and no test here writes it.
 *
 * @author Ivan Rodriguez
 */
class NoiseFloorGateTest {

	private static final List<MutationOperator> OPERATORS = PolicyImprover.SCORED_MUTATIONS;

	private static final String T01 = "b27-t01";

	private static final String T02 = "b27-t02";

	private static final String T03 = "b27-t03";

	private static final String T06 = "b27-t06";

	private static final String[] ALL = { T01, T02, T03, T06 };

	private static final String[] THREE = { T01, T02, T03 };

	private static final String[] TWO = { T01, T02 };

	private static final String[] ONE = { T01 };

	@TempDir
	Path work;

	private final ModelCallMeter meter = new ModelCallMeter();

	@Test
	void aDeltaInsideTheFloorIsNoImprovementAndADeltaOutsideItIs() {
		// One task of six is a 0.167 move, which is what a borderline answer flipping
		// between two runs of the same pack produces for free.
		NoiseFloor wide = NoiseFloor.injected(0.2, "test");
		Improvement inside = Improvement.paired(OuterLoopFixtures.scoreOf(TWO), OuterLoopFixtures.scoreOf(THREE), wide,
				"b27-v", "b27-parent");

		assertThat(inside.delta()).isBetween(0.16, 0.17);
		assertThat(inside.accepted()).isFalse();
		assertThat(inside.verdict()).isEqualTo(Improvement.NO_IMPROVEMENT);

		Improvement outside = Improvement.paired(OuterLoopFixtures.scoreOf(TWO), OuterLoopFixtures.scoreOf(THREE),
				NoiseFloor.injected(0.05, "test"), "b27-v", "b27-parent");
		assertThat(outside.accepted()).isTrue();
		assertThat(outside.verdict()).isEqualTo(Improvement.IMPROVEMENT);
	}

	@Test
	void aMoveInsideTheFloorIsRefusedByTheGateAndNotHeld() {
		OuterRun run = runWithFloor(0.2);
		VariantRecord oneTaskBetter = variant(run, 2, MutationOperator.KEYWORD_INSTEAD_OF_PREFIX);

		assertThat(oneTaskBetter.frozenScore())
			.isGreaterThan(run.archive().frozenScoreOf(oneTaskBetter.parentVariantId()));
		assertThat(oneTaskBetter.improvement().delta()).isBetween(0.16, 0.17);
		assertThat(oneTaskBetter.improvement().verdict()).isEqualTo(Improvement.NO_IMPROVEMENT);
		assertThat(oneTaskBetter.improvement().accepted()).isFalse();
		assertThat(oneTaskBetter.gated()).isFalse();
		assertThat(oneTaskBetter.gateStage()).isEqualTo("baseline");
		assertThat(run.heldIds()).doesNotContain(oneTaskBetter.variantId());
	}

	@Test
	void theSameMoveClearsATighterFloorAndIsGatedAndHeld() {
		// Control: identical run, identical scores, floor tightened below one task. Only
		// the
		// floor changed, so what this test separates is the floor and nothing else.
		OuterRun run = runWithFloor(0.05);
		VariantRecord oneTaskBetter = variant(run, 2, MutationOperator.KEYWORD_INSTEAD_OF_PREFIX);

		assertThat(oneTaskBetter.frozenScore())
			.isEqualTo(run.archive().frozenScoreOf(oneTaskBetter.parentVariantId()) + (1.0 / 6.0), offset(1e-9));
		assertThat(oneTaskBetter.improvement().verdict()).isEqualTo(Improvement.IMPROVEMENT);
		assertThat(oneTaskBetter.gated()).isTrue();
		assertThat(run.heldIds()).contains(oneTaskBetter.variantId());
	}

	private OuterRun runWithFloor(double floor) {
		FrozenTaskSet tasks = OuterLoopFixtures.frozenTasks();
		PolicyImprover gen1 = PolicyImprover.surviving(PolicyPrompt.frozenSeed());
		PolicyImprover gen2 = gen1
			.derive(OuterLoopFixtures.proposalFor(gen1.propose(OPERATORS), MutationOperator.SEED));
		Map<String, String[]> gen1Passing = Map.of("SEED", THREE, "KEYWORD_INSTEAD_OF_PREFIX", TWO, "DROP_EXCLUSION",
				ONE, "DROP_TRIGGER", ONE, "ADD_LATENCY_TRIGGER", ONE);
		// Generation 2's unchanged policy repeats its parent exactly; one mutation is
		// worth
		// exactly one task more.
		Map<String, String[]> gen2Passing = Map.of("SEED", THREE, "KEYWORD_INSTEAD_OF_PREFIX", ALL, "DROP_EXCLUSION",
				ONE, "DROP_TRIGGER", ONE, "ADD_LATENCY_TRIGGER", ONE);
		Map<String, Map<String, String>> rows = new LinkedHashMap<>(
				OuterLoopFixtures.tableOf(gen1, OPERATORS, gen1Passing));
		rows.putAll(OuterLoopFixtures.tableOf(gen2, OPERATORS, gen2Passing));
		Referee referee = OuterLoopFixtures.referee(tasks, rows, this.meter);
		return new OuterLoop(referee, this.meter, new HeldForApproval()).run(new OuterLoop.Request(gen1, OPERATORS,
				Baseline.injected(OuterLoopFixtures.scoreOf(), "a score this test chooses, not the measured baseline"),
				NoiseFloor.injected(floor, "a threshold this test chooses, not the measured artifact"),
				Budget.declare(200, 2), this.work));
	}

	private static VariantRecord variant(OuterRun run, int generation, MutationOperator operator) {
		return run.archive()
			.all()
			.stream()
			.filter((record) -> record.generation() == generation && record.operator().equals(operator.name()))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("generation " + generation + " scored no " + operator));
	}

}
