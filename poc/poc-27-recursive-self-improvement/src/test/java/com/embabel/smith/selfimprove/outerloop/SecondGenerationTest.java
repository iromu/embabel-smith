/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.outerloop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
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
 * Task 7.3: generation 2's improver comes from generation 1's output, and the report
 * states an improvement per generation instead of one score for the run.
 *
 * <p>
 * Offline; see {@link OuterLoopFixtures} for what the scores here do and do not mean.
 *
 * @author Ivan Rodriguez
 */
class SecondGenerationTest {

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
	void theSecondGenerationsImproverIsBuiltFromTheFirstGenerationsSelection() {
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

		assertThat(run.generations()).hasSize(2);
		GenerationReport first = run.generations().get(0);
		GenerationReport second = run.generations().get(1);

		// Derivation, not re-seeding: generation 2's improver names the variant
		// generation 1
		// selected, and the variants it scored belong to it.
		assertThat(second.derivedFrom()).isEqualTo(first.bestVariantId());
		assertThat(second.improverId()).isEqualTo(gen2.id());
		assertThat(run.archive().all()).filteredOn((record) -> record.generation() == 2)
			.map(VariantRecord::improverId)
			.containsOnly(gen2.id());
		assertThat(gen2.parentVariantId()).isEqualTo(first.bestVariantId());
		assertThat(gen2.generation()).isEqualTo(2);

		// One move per generation, each paired with its own reference: generation 1 is
		// paired against the run's baseline, generation 2 against what generation 1 left
		// behind.
		assertThat(first.improvement().referenceVariantId()).isNull();
		assertThat(second.improvement().referenceVariantId()).isEqualTo(first.bestVariantId());
		assertThat(second.improvement().referenceScore()).isEqualTo(first.bestScore(), offset(1e-9));
		assertThat(first.improvement().delta()).isNotEqualTo(second.improvement().delta());
		assertThat(second.improvement().verdict())
			.as("generation 2 matched but did not beat what generation 1 already reached")
			.isEqualTo(Improvement.NO_IMPROVEMENT);

		String report = run.describe();
		assertThat(report).contains("generation 1").contains("generation 2");
	}

	@Test
	void theRunCarriesNoSingleScoreToQuote() {
		// Type-level, not name-level: a run type with any double-returning accessor has a
		// number someone can quote out of context. GenerationReport is the control — it
		// is
		// the per-generation type, it does carry one, and this proves the detector sees
		// one
		// when it is there.
		assertThat(doubleReturningAccessors(OuterRun.class)).isEmpty();
		assertThat(doubleReturningAccessors(GenerationReport.class)).contains("bestScore");
	}

	@Test
	void anOperatorThatDoesNotFitThisPromptIsReportedRatherThanQuietlyDropped() {
		// KEYWORD_INSTEAD_OF_PREFIX needs an exclusion matched by message prefix and
		// DROP_EXCLUSION needs any exclusion at all. A prompt with neither cannot be
		// mutated
		// either way, and the operators throw rather than emitting something they did not
		// name.
		PolicyPrompt stripped = PolicyPrompt
			.parse("POLICY b27-stripped\nTRIGGER failed_step WHEN failed\nTRIGGER content_drift WHEN hash_differs\n");
		PolicyImprover improver = PolicyImprover.surviving(stripped);

		PolicyImprover.Batch batch = improver.propose(OPERATORS);

		assertThat(batch.notOffered()).hasSize(2).allSatisfy((refusal) -> assertThat(refusal).contains("b27-stripped"));
		assertThat(batch.notOffered())
			.anyMatch((refusal) -> refusal.startsWith(MutationOperator.KEYWORD_INSTEAD_OF_PREFIX.name()));
		assertThat(batch.notOffered())
			.anyMatch((refusal) -> refusal.startsWith(MutationOperator.DROP_EXCLUSION.name()));
		assertThat(batch.proposals()).map(PolicyImprover.Proposal::operator)
			.containsExactly("SEED", MutationOperator.DROP_TRIGGER.name(), MutationOperator.ADD_LATENCY_TRIGGER.name());

		Map<String, String[]> passing = Map.of("SEED", ONE, "DROP_TRIGGER", ONE, "ADD_LATENCY_TRIGGER", ONE);
		Referee referee = OuterLoopFixtures.referee(OuterLoopFixtures.frozenTasks(),
				OuterLoopFixtures.tableOf(improver, OPERATORS, passing), this.meter);
		OuterRun run = new OuterLoop(referee, this.meter, new HeldForApproval()).run(new OuterLoop.Request(improver,
				OPERATORS, Baseline.injected(OuterLoopFixtures.scoreOf(), "task 2.4 has not recorded a baseline"),
				NoiseFloor.injected(0.05, "task 2.5 has not measured a noise floor"), Budget.declare(100, 1),
				this.work));

		assertThat(run.notMeasured())
			.anyMatch((gap) -> gap.contains("could not apply 2 of " + OPERATORS.size() + " operators"));
	}

	private static List<String> doubleReturningAccessors(Class<?> type) {
		return Arrays.stream(type.getDeclaredMethods())
			.filter((method) -> method.getReturnType() == double.class || method.getReturnType() == Double.class)
			.map(Method::getName)
			.toList();
	}

}
