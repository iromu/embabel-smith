/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.baseline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.embabel.smith.selfimprove.innerloop.ModelCallMeter;
import com.embabel.smith.selfimprove.referee.FrozenTask;
import com.embabel.smith.selfimprove.referee.FrozenTaskSet;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.within;

/**
 * Task 6.1: the task files added to give the builder packs something to climb are
 * authored honestly, and the headroom check that reports them still has a stop branch.
 *
 * <p>
 * The four added tasks interrogate project rules whose general prior points the other way
 * — an advisory check that cannot block however severe the finding is, an opt-in scan
 * that a default build never runs, a publish that is refused rather than
 * committed-out-of-attainable-reach, and a retired pack the loop may not touch. Those are
 * the questions the unmutated packs are expected to get wrong, which is the point of
 * adding them: a seed already at the ceiling leaves the strict comparison nothing to do,
 * the failure mode {@code benchmark/tasks/README.md} records for the six it started with.
 *
 * <p>
 * No model is called. The answers come from {@link BaselineFixtures} for the six
 * originals and from the tables below for the added four, through the same
 * {@link com.embabel.smith.builder.core.SpecPromptCaller} seam the live run uses, so the
 * call count is read off {@link ModelCallMeter} rather than assumed.
 *
 * @author Ivan Rodriguez
 */
class AddedHeadroomTasksTest {

	/**
	 * The frozen six plus the four headroom tasks, as one set. The added files live in
	 * {@code benchmark/tasks-headroom/} rather than in the frozen directory on purpose:
	 * the launcher scores against the frozen six, whose file count drives its declared
	 * budget and the {@code 2/6} figure its evidence records, and ten tasks would declare
	 * 110 calls against a 5400 s wall-clock ceiling that refuses the run before the first
	 * call (see {@code benchmark/tasks/README.md}). This class is the only thing that
	 * reads the two directories together, which is what lets the headroom claim be
	 * checked without moving anyone else's denominator.
	 */
	private static final FrozenTaskSet TASKS = frozenPlusHeadroom();

	/**
	 * Copies both task directories into one temporary directory and loads it, because
	 * {@link FrozenTaskSet#load(Path)} reads a single directory and the set has no union
	 * operation — adding one to the class under test for a single caller is not worth it.
	 * @return the ten tasks, in file-name order
	 */
	private static FrozenTaskSet frozenPlusHeadroom() {
		Path benchmark = BaselineFixtures.resources().resolve("benchmark");
		try {
			Path merged = Files.createTempDirectory("poc27-headroom-set");
			merged.toFile().deleteOnExit();
			for (String dir : List.of("tasks", "tasks-headroom")) {
				try (var files = Files.list(benchmark.resolve(dir))) {
					for (Path file : files.filter((path) -> path.getFileName().toString().endsWith(".json")).toList()) {
						Path copy = merged.resolve(file.getFileName());
						Files.copy(file, copy);
						copy.toFile().deleteOnExit();
					}
				}
			}
			return FrozenTaskSet.load(merged);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("cannot assemble the frozen-plus-headroom task set", ex);
		}
	}

	/**
	 * The identifiers task 6.1 added. They are asserted against the loaded set rather
	 * than against a directory listing, so a task file that never reached the classpath
	 * fails here instead of making every assertion below quietly vacuous.
	 */
	private static final List<String> ADDED = List.of("b27-t07", "b27-t08", "b27-t09", "b27-t10");

	/**
	 * What an unmodified process answers to the added tasks: the prior each one is built
	 * to catch, in the same register as {@link BaselineFixtures#PRIORS}.
	 */
	private static final Map<String, String> ADDED_PRIORS = Map.of("b27-t07",
			"Yes. A finding raised at the highest severity a check reports blocks publication of the pack until the"
					+ " personal data is removed or the finding is dismissed.",
			"b27-t08",
			"Yes. Every pipeline that publishes files checks them for embedded credentials, and a build reaching"
					+ " publication has cleared that check.",
			"b27-t09",
			"Yes. The loop commits the candidate to the git remote and leaves the push for whoever holds the"
					+ " credentials, which is the default posture for a shared repository.",
			"b27-t10",
			"Yes. Failures in the traces are precisely the evidence the loop wants, so a pack with failing runs is"
					+ " queued first regardless of the state written beside it.");

	/**
	 * Answers written from the rules the added tasks interrogate, so the ceiling case
	 * covers the whole set rather than the six it was measured on.
	 */
	private static final Map<String, String> ADDED_CORRECT = Map.of("b27-t07",
			"No. The `pii` check is advisory by check name, not by severity, so a finding from it never blocks"
					+ " publication; the checks that block are schema, version, quality, unicode, lint and license.",
			"b27-t08",
			"No. The checks that look for leaked credentials — security, secrets and code-integrity — shell out to"
					+ " third-party binaries, so they are not run by default; an explicit --full-inspection asks for"
					+ " them.",
			"b27-t09",
			"No. A git: source with pushing off is refused rather than published: the publisher commits inside a"
					+ " temporary clone that its close() deletes, so nothing would survive, and the candidate stays"
					+ " in the approval queue.",
			"b27-t10",
			"No. A retired pack is excluded from automatic refinement, and the documented way back is an explicit"
					+ " rebuild by a human that republishes it.");

	@Test
	void theAddedTaskFilesAreLoadedAndEveryOneStatesItsOwnCheck() {
		assertThat(TASKS.ids()).as("a task file that did not reach the classpath is not a task").containsAll(ADDED);
		for (FrozenTask task : TASKS.tasks()) {
			// The check field is the whole contract: the file states its own expectation
			// so a
			// scorer needs nothing outside it. An empty one is readable by the loader — a
			// missing
			// key is not — which is exactly why it needs asserting rather than assuming.
			assertThat(task.check()).as(task.id() + " carries no check, so its answer is guessable").isNotBlank();
		}
	}

	@Test
	void noAddedTaskHandsOutItsOwnAnswer() {
		// The control case first. An absence asserted by field name is defeated by
		// naming, so
		// the guard is shown to fire on a task that does restate its answer before it is
		// used to
		// certify the frozen set.
		FrozenTask giveaway = new FrozenTask("b27-x99", "gate", "Does the scanner use CRITICAL?", List.of("critical"),
				List.of(), List.of(), "Authored to be caught by the invariant.");
		assertThat(giveaway.restatedPhrases()).as("the control must be caught, or the guard proves nothing")
			.containsExactly("critical");

		assertThatCode(TASKS::assertNoRestatedPhrases).doesNotThrowAnyException();
		for (FrozenTask task : TASKS.tasks()) {
			if (ADDED.contains(task.id())) {
				assertThat(task.restatedPhrases()).as(task.id() + " can be passed by echoing the question back")
					.isEmpty();
			}
		}
	}

	@Test
	void theUnmodifiedProcessFailsEveryAddedTaskAndSaysThereIsRoomToMove() {
		Baseline baseline = baselineWith(merged(BaselineFixtures.PRIORS, ADDED_PRIORS));

		assertThat(baseline.baselineFailingTaskIds()).as("the added tasks are headroom or they are decoration")
			.containsAll(ADDED);
		assertThat(baseline.humanLabelFailingTaskIds()).containsAll(ADDED);
		assertThat(baseline.baselineScore()).as("the gate arm has somewhere left to go").isLessThan(1.0);
		assertThat(baseline.headroom()).as(baseline.verdict()).isTrue();
		assertThat(baseline.verdict()).contains("room to move");
	}

	@Test
	void anAllCorrectAnswerSetOverTheWholeSetReportsNoHeadroomAndTellsTheLoopToStop() {
		// The non-vacuity case task 6.1 is required to add. The headroom check has only
		// ever
		// been seen reporting 2/6 on the six it shipped with, which does not show it is a
		// check:
		// a ceiling answer set over the enlarged set has to take the stop branch, or the
		// added
		// tasks would buy headroom by existing rather than by being answered.
		Baseline baseline = baselineWith(merged(BaselineFixtures.CORRECT, ADDED_CORRECT));

		assertThat(baseline.baselineScore()).isCloseTo(1.0, within(1e-9));
		assertThat(baseline.humanLabelScore()).isCloseTo(1.0, within(1e-9));
		assertThat(baseline.headroom()).as(baseline.verdict()).isFalse();
		assertThat(baseline.verdict()).contains("STOP").contains("the outer loop cannot run");
	}

	@Test
	void theAddedTasksAreNarrowedAndEveryOneIsAskedOncePerMeasurement() {
		// Narrowed means the gate's single substring cannot carry the task, so the added
		// four
		// are places the two arms can disagree — the same gap the six originals record
		// rather
		// than a new one.
		assertThat(TASKS.narrowedTaskIds()).containsAll(ADDED);

		ModelCallMeter meter = new ModelCallMeter();
		Map<String, String> ceiling = merged(BaselineFixtures.CORRECT, ADDED_CORRECT);
		new BaselineRun(TASKS, BaselineFixtures.canned(TASKS, ceiling), meter).measure();

		// Measured off the meter: the added prompts are asked, once each, so the paid run
		// costs
		// the set's size rather than the size the budget was declared against.
		assertThat(meter.count()).isEqualTo(TASKS.ids().size());
	}

	private static Map<String, String> merged(Map<String, String> originals, Map<String, String> added) {
		Map<String, String> answers = new LinkedHashMap<>(originals);
		answers.putAll(added);
		return answers;
	}

	private static Baseline baselineWith(Map<String, String> answers) {
		ModelCallMeter meter = new ModelCallMeter();
		ScoredAnswers scores = new BaselineRun(TASKS, BaselineFixtures.canned(TASKS, answers), meter).measure();
		return Baseline.of(TASKS, scores, meter.count());
	}

}
