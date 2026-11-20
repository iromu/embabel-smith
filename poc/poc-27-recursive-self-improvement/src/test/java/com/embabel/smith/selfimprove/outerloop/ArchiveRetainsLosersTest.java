/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.outerloop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
 * Task 7.2: the archive keeps every variant it scored, including the ones that scored
 * below the variant they came from, and an archive query can find a loser by its parent
 * pointer.
 *
 * <p>
 * Offline; see {@link OuterLoopFixtures} for what the scores here do and do not mean.
 *
 * @author Ivan Rodriguez
 */
class ArchiveRetainsLosersTest {

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
	void aVariantThatLostToItsParentIsStillRetainedAndStillFindableByParent() {
		FrozenTaskSet tasks = OuterLoopFixtures.frozenTasks();
		PolicyImprover gen1 = PolicyImprover.surviving(PolicyPrompt.frozenSeed());
		// Generation 1's unchanged policy wins outright; generation 2's improver is
		// derived
		// from it and every variant that improver proposes scores below it.
		Map<String, String[]> gen1Passing = Map.of("SEED", ALL, "KEYWORD_INSTEAD_OF_PREFIX", ONE, "DROP_EXCLUSION", ONE,
				"DROP_TRIGGER", ONE, "ADD_LATENCY_TRIGGER", ONE);
		Map<String, String[]> gen2Passing = Map.of("SEED", ONE, "KEYWORD_INSTEAD_OF_PREFIX", ONE, "DROP_EXCLUSION", ONE,
				"DROP_TRIGGER", ONE, "ADD_LATENCY_TRIGGER", ONE);
		PolicyImprover gen2 = gen1
			.derive(OuterLoopFixtures.proposalFor(gen1.propose(OPERATORS), MutationOperator.SEED));

		Map<String, Map<String, String>> rows = new java.util.LinkedHashMap<>(
				OuterLoopFixtures.tableOf(gen1, OPERATORS, gen1Passing));
		rows.putAll(OuterLoopFixtures.tableOf(gen2, OPERATORS, gen2Passing));
		Referee referee = OuterLoopFixtures.referee(tasks, rows, this.meter);

		OuterRun run = new OuterLoop(referee, this.meter, new HeldForApproval()).run(new OuterLoop.Request(gen1,
				OPERATORS, Baseline.injected(OuterLoopFixtures.scoreOf(), "task 2.4 has not recorded a baseline"),
				NoiseFloor.injected(0.05, "task 2.5 has not measured a noise floor"), Budget.declare(200, 2),
				this.work));

		assertThat(run.generations()).hasSize(2);
		assertThat(run.archive().size()).isEqualTo(2 * (1 + OPERATORS.size()));

		String parent = gen1.id();
		assertThat(run.generations().get(1).derivedFrom()).isEqualTo(parent);

		List<VariantRecord> children = run.archive().withParent(parent);
		assertThat(children).isNotEmpty();
		double parentScore = run.archive().frozenScoreOf(parent);
		assertThat(children).anySatisfy((child) -> assertThat(child.generation()).isEqualTo(2));
		assertThat(children).allSatisfy((child) -> assertThat(child.frozenScore()).isLessThan(parentScore));

		// The loser is reported as what it is, and it is still in the archive.
		VariantRecord loser = children.stream()
			.filter((child) -> child.generation() == 2 && child.operator().equals("SEED"))
			.findFirst()
			.orElseThrow();
		assertThat(loser.frozenScore()).isEqualTo(OuterLoopFixtures.scoreOf(T01), offset(1e-9));
		assertThat(loser.improvement().delta()).isLessThan(0.0);
		assertThat(loser.improvement().verdict()).isEqualTo(Improvement.REGRESSION);
		assertThat(run.archive().byVariant(loser.variantId())).isPresent();
	}

	@Test
	void theArchiveRefusesToReplaceAVariantItAlreadyHolds() {
		Archive archive = new Archive();
		archive.retain(record("b27-a", null, 0.5));
		archive.retain(record("b27-b", "b27-a", 0.25));

		assertThat(archive.withParent("b27-a")).extracting(VariantRecord::variantId).containsExactly("b27-b");
		assertThat(archive.withParent(null)).extracting(VariantRecord::variantId).containsExactly("b27-a");
		assertThat(archive.best()).map(VariantRecord::variantId).contains("b27-a");

		assertThatThrownBy(() -> archive.retain(record("b27-a", null, 1.0)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("append-only");
		assertThat(archive.frozenScoreOf("b27-a")).isEqualTo(0.5);
	}

	private static VariantRecord record(String variantId, String parentId, double score) {
		return new VariantRecord(1, variantId, parentId, "b27-improver", "SEED", false, "baseline", null, score,
				Improvement.paired(0.0, score, NoiseFloor.injected(0.05, "test"), variantId, parentId), 6, 1L);
	}

}
