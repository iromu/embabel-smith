/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.outerloop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import java.nio.file.Files;
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
 * Task 7.1: the surviving offline policy runs as generation 1's improver, its candidates
 * are scored through the referee, and the run records a score, a parent and a cost for
 * every one of them.
 *
 * <p>
 * Offline. Scores come from the answer table in {@link OuterLoopFixtures}, so what these
 * tests establish is that the loop records and links and charges correctly — not that any
 * model is getting better.
 *
 * @author Ivan Rodriguez
 */
class OuterGenerationRecordTest {

	private static final List<MutationOperator> OPERATORS = PolicyImprover.SCORED_MUTATIONS;

	private static final String T01 = "b27-t01";

	private static final String T02 = "b27-t02";

	private static final String T03 = "b27-t03";

	private static final String T06 = "b27-t06";

	@TempDir
	Path work;

	private final ModelCallMeter meter = new ModelCallMeter();

	@Test
	void everyScoredVariantRecordsItsScoreItsParentAndItsCost() throws Exception {
		FrozenTaskSet tasks = OuterLoopFixtures.frozenTasks();
		Map<String, String[]> passing = Map.of("SEED", new String[] { T01, T02, T03 }, "KEYWORD_INSTEAD_OF_PREFIX",
				new String[] { T01, T02, T03, T06 }, "DROP_EXCLUSION", new String[] { T01 }, "DROP_TRIGGER",
				new String[] { T02 }, "ADD_LATENCY_TRIGGER", new String[] {});
		PolicyImprover improver = PolicyImprover.surviving(PolicyPrompt.frozenSeed());
		OuterRun run = run(tasks, improver, passing, Budget.declare(100, 1));

		assertThat(run.archive().size()).isEqualTo(1 + OPERATORS.size());
		for (VariantRecord record : run.archive().all()) {
			assertThat(record.frozenScore()).as("frozen score recorded for %s", record.variantId())
				.isEqualTo(OuterLoopFixtures.scoreOf(passing.get(record.operator())), offset(1e-9));
			assertThat(record.modelCalls()).as("model calls charged to %s", record.variantId())
				.isEqualTo(tasks.ids().size());
			assertThat(record.parentVariantId()).as("parent pointer of %s", record.variantId())
				.isEqualTo(record.operator().equals("SEED") ? null : improver.id());
		}
		assertThat(run.modelCalls()).isEqualTo((1 + OPERATORS.size()) * tasks.ids().size());

		// The gate's own number is kept next to the frozen one rather than merged into
		// it.
		// They are different measurements — the gate scores a narrowed substring match,
		// the
		// frozen check runs the full require/anyOf/forbid — and they are only asserted
		// equal
		// for the variant that answers everything right, which is the agreement the
		// referee package's own fixtures already establish.
		VariantRecord allCorrect = run.archive()
			.all()
			.stream()
			.filter((record) -> record.frozenScore() == 1.0)
			.findFirst()
			.orElseThrow();
		assertThat(allCorrect.gateScore()).isNotNull().isEqualTo(1.0);
		assertThat(allCorrect.gated()).isTrue();

		List<String> lines = Files.readAllLines(run.recordFile());
		assertThat(lines).hasSize(1 + OPERATORS.size());
		assertThat(lines).allSatisfy(
				(line) -> assertThat(line).contains("frozenScore").contains("parentVariantId").contains("modelCalls"));
	}

	@Test
	void generationOneIsThePolicyThatSurvivedTheOfflineInnerLoop() {
		// The inner loop's own convergence test establishes that the frozen seed is the
		// variant that survives it, so the policy handed to the outer loop here is that
		// survivor, used unchanged: nothing in this package re-seeds or re-tunes it.
		PolicyPrompt survivor = PolicyPrompt.frozenSeed();
		PolicyImprover improver = PolicyImprover.surviving(survivor);

		assertThat(improver.id()).isEqualTo(survivor.name());
		assertThat(improver.generation()).isEqualTo(1);
		assertThat(improver.parentVariantId()).isNull();

		FrozenTaskSet tasks = OuterLoopFixtures.frozenTasks();
		Map<String, String[]> passing = Map.of("SEED", new String[] { T01, T02, T03 }, "KEYWORD_INSTEAD_OF_PREFIX",
				new String[] { T01 }, "DROP_EXCLUSION", new String[] { T01 }, "DROP_TRIGGER", new String[] { T01 },
				"ADD_LATENCY_TRIGGER", new String[] { T01 });
		OuterRun run = run(tasks, improver, passing, Budget.declare(100, 1));

		GenerationReport first = run.generations().get(0);
		assertThat(first.improverId()).isEqualTo(survivor.name());
		assertThat(first.derivedFrom()).isNull();
		assertThat(first.scoredVariants()).isEqualTo(1 + OPERATORS.size());

		// The survivor itself is one of the candidates, scored, and it has no parent: it
		// is
		// where the lineage starts.
		VariantRecord itself = run.archive().byVariant(survivor.name()).orElseThrow();
		assertThat(itself.operator()).isEqualTo("SEED");
		assertThat(itself.parentVariantId()).isNull();
		assertThat(itself.frozenScore()).isEqualTo(OuterLoopFixtures.scoreOf(T01, T02, T03), offset(1e-9));
	}

	private OuterRun run(FrozenTaskSet tasks, PolicyImprover improver, Map<String, String[]> passing, Budget budget) {
		Referee referee = OuterLoopFixtures.referee(tasks, OuterLoopFixtures.tableOf(improver, OPERATORS, passing),
				this.meter);
		return new OuterLoop(referee, this.meter, new HeldForApproval()).run(new OuterLoop.Request(improver, OPERATORS,
				Baseline.injected(OuterLoopFixtures.scoreOf(), "a score this test chooses, not the measured baseline"),
				NoiseFloor.injected(0.05, "a threshold this test chooses, not the measured artifact"), budget,
				this.work));
	}

}
