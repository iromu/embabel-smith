/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.outerloop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.embabel.smith.builder.eval.InvokerFactory;
import com.embabel.smith.selfimprove.innerloop.PolicyGenerator.MutationOperator;
import com.embabel.smith.selfimprove.referee.FrozenTaskSet;
import com.embabel.smith.selfimprove.referee.Referee;

/**
 * Tasks 7.1 and 7.2: the cost one candidate is charged against the ceiling is arithmetic
 * from the frozen task count rather than a number carried in code, and a pass whose
 * arithmetic does not fit the wall clock is refused before it makes a call.
 *
 * <p>
 * Offline throughout, and no score here is evidence about a model — see
 * {@link OuterLoopFixtures} for what a canned answer table does and does not measure.
 * What these tests measure is the arithmetic, which needs no model: a different task
 * count is a directory holding a different number of the checked-in task files, never a
 * restated benchmark.
 *
 * @author Ivan Rodriguez
 */
class SpendCeilingTest {

	private static final List<MutationOperator> OPERATORS = PolicyImprover.SCORED_MUTATIONS;

	@TempDir
	Path dir;

	@Test
	void theCostIsOneDeployPlusOneInvocationPerFrozenTaskAndMovesWithTheTaskCount() {
		// The point of calling it arithmetic is that it moves: two task files must not
		// declare what five declare, or the ceiling is a constant in the costume of a
		// calculation and a widened benchmark would silently outrun it.
		FrozenTaskSet two = frozenSetOf(2);
		FrozenTaskSet five = frozenSetOf(5);

		assertThat(Budget.costPerCandidate(two.ids().size())).isEqualTo(1 + 2);
		assertThat(Budget.costPerCandidate(five.ids().size())).isEqualTo(1 + 5);
		assertThat(OuterLoopProcess.budget(two).maxModelCalls()).as("a smaller frozen set declares a smaller ceiling")
			.isLessThan(OuterLoopProcess.budget(five).maxModelCalls());

		int generations = OuterLoopProcess.budget(five).maxGenerations();
		assertThat(OuterLoopProcess.budget(five).maxModelCalls())
			.isEqualTo((1 + OPERATORS.size()) * (1 + 5) * generations);
	}

	@Test
	void theCheckedInSetDeclaresSeventyCallsAndFitsTheCeilingItIsGiven() {
		// The number the launcher prints, pinned: 5 variants (the seed plus its 4 scored
		// mutations) x (1 deploy + 6 task invocations) x 2 generations. It also has to
		// fit
		// the wall-clock ceiling it is printed beside, or the constant would be refusing
		// the
		// pass this POC documents rather than the oversized one its record argues
		// against.
		FrozenTaskSet frozen = OuterLoopFixtures.frozenTasks();

		assertThat(OuterLoopProcess.budget(frozen).maxModelCalls()).isEqualTo(70);
		assertThat(OuterLoopProcess.wallClockRefusalReason(OuterLoopProcess.budget(frozen))).isNull();
	}

	@Test
	void anOverCeilingBudgetIsRefusedWithTheArithmeticItRefusedOn() {
		// 90 declared calls at 60 s each is exactly the ceiling, so the boundary is the
		// ceiling and not a number comfortably inside it.
		assertThat(OuterLoopProcess.wallClockRefusalReason(Budget.declare(90, 2))).isNull();
		assertThat(OuterLoopProcess.wallClockRefusalReason(Budget.declare(91, 2))).contains("91 declared calls")
			.contains("5400")
			.contains("before the first call");
	}

	@Test
	void anOverCeilingRunRefusesOnTimeBeforeAnyCallIsSpent() {
		// Nine frozen tasks declare 5 x (1 + 9) x 2 = 100 calls, which at the measured
		// per-call worst case is 6000 s against a 5400 s ceiling. The fake invoker throws
		// if
		// the launcher reaches it at all, and the counter says it never did.
		AtomicInteger calls = new AtomicInteger();

		assertThatThrownBy(() -> OuterLoopProcess.runPass(resourcesWith(9), counting(calls), this.dir.resolve("work")))
			.isInstanceOf(OuterLoopRefused.class)
			.hasMessageContaining("wall-clock ceiling")
			.hasMessageContaining("100 declared calls")
			.hasMessageContaining("6000")
			.hasMessageContaining("5400");
		assertThat(calls.get()).as("the refusal is ahead of any call").isZero();
	}

	/**
	 * A factory that fails the test if the launcher reaches it, counting the attempt.
	 * @param calls the counter to bump
	 * @return the factory.
	 */
	private static InvokerFactory counting(AtomicInteger calls) {
		return (pack) -> (input) -> {
			calls.incrementAndGet();
			throw new IllegalStateException("a run refused on the wall clock must not make a call");
		};
	}

	private FrozenTaskSet frozenSetOf(int count) {
		Path resources = resourcesWith(count);
		return FrozenTaskSet.load(resources.resolve(Referee.TASKS));
	}

	/**
	 * A resources directory holding {@code count} frozen task files: the checked-in ones,
	 * cycled if that is not enough. Copied rather than authored, because a fixture that
	 * wrote its own task text would be changing the benchmark the launcher scores
	 * against.
	 * <p>
	 * No evidence artifacts: the wall-clock refusal is asked before the launcher looks at
	 * either of them, which is part of what the refusal test is testing.
	 * @param count the number of task files to stand up
	 * @return the directory to point the launcher at.
	 */
	private Path resourcesWith(int count) {
		Path resources = this.dir.resolve("resources-" + count);
		Path tasks = resources.resolve(Referee.TASKS);
		try {
			Files.createDirectories(tasks);
			List<Path> checkedIn;
			try (Stream<Path> paths = Files.list(OuterLoopFixtures.resources().resolve(Referee.TASKS))) {
				checkedIn = paths.filter((path) -> path.getFileName().toString().endsWith(".json")).sorted().toList();
			}
			for (int i = 0; i < count; i++) {
				Files.copy(checkedIn.get(i % checkedIn.size()), tasks.resolve("task-" + i + ".json"));
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException("cannot stage " + count + " frozen task files under " + tasks, ex);
		}
		return resources;
	}

}
