/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.outerloop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.embabel.smith.builder.eval.InvokerFactory;
import com.embabel.smith.selfimprove.baseline.EvidenceWriter;
import com.embabel.smith.selfimprove.referee.Referee;

/**
 * Task 6.4, both cases: a candidate that answers two different task prompts with one
 * string stops the launcher with a named refusal, and a candidate that varies its answer
 * passes the same check.
 *
 * <p>
 * One case alone would prove nothing — a guard that refuses everything and a guard that
 * refuses nothing both pass a single-case test — so the varying invoker is not a control
 * written for coverage, it is the case that shows the refusal is conditional.
 *
 * <p>
 * The check belongs in the launcher and fires after the first two answers come back,
 * which the call count in the first test is what asserts: two calls spent, not the
 * seventy the run declared. That is the difference between an early stop and a
 * post-mortem. This is offline, so nothing here is evidence about a model; see
 * {@link OuterLoopFixtures} for what these canned answers measure.
 *
 * @author Ivan Rodriguez
 */
class DistinctAnswerGuardTest {

	/**
	 * What the constant candidate answers every prompt with, short enough to quote whole.
	 */
	private static final String CONSTANT_ANSWER = "No. The scanner rates findings from INFO to CRITICAL.";

	@TempDir
	Path dir;

	@Test
	void aCandidateAnsweringEveryTaskTheSameStopsTheLauncherWithTheNamedRefusal() throws IOException {
		AtomicInteger calls = new AtomicInteger();
		Path resources = evidenceTree();

		assertThatThrownBy(() -> OuterLoopProcess.runPass(resources, constant(calls), this.dir.resolve("work")))
			.isInstanceOf(OuterLoopRefused.class)
			.hasMessageContaining("one answer to 2 different task prompts")
			.hasMessageContaining("all 2 answers")
			.hasMessageContaining(CONSTANT_ANSWER);
		assertThat(calls.get()).as("two answers were enough to see it; the other fifty-eight were never bought")
			.isEqualTo(2);
	}

	@Test
	void aCandidateThatVariesItsAnswerPassesTheSameCheck() throws IOException {
		AtomicInteger calls = new AtomicInteger();
		Path resources = evidenceTree();

		// The same launcher, the same guard, the same two first tasks: nothing to refuse,
		// so
		// the pass goes on and scores the variants it declared.
		assertThatCode(() -> OuterLoopProcess.runPass(resources, varying(calls), this.dir.resolve("work")))
			.doesNotThrowAnyException();
		assertThat(calls.get()).as("the run went on past the two answers the guard looks at").isGreaterThan(2);
	}

	/**
	 * A candidate that gives one answer to everything — the shape of the invoker that let
	 * POC 27's live pass spend its whole budget on a candidate that never saw its input.
	 * @param calls the counter to bump for every call made
	 * @return the factory.
	 */
	private static InvokerFactory constant(AtomicInteger calls) {
		return (pack) -> (input) -> {
			calls.incrementAndGet();
			return CONSTANT_ANSWER;
		};
	}

	/**
	 * A candidate whose answers differ per prompt, which is what an actually-conditioned
	 * candidate looks like from outside.
	 * @param calls the counter to bump for every call made
	 * @return the factory.
	 */
	private static InvokerFactory varying(AtomicInteger calls) {
		return (pack) -> (input) -> {
			calls.incrementAndGet();
			return "an answer conditioned on " + input;
		};
	}

	/**
	 * A resources directory holding the frozen task files as checked in plus the two
	 * artifacts the launcher requires, in the permissive case: headroom recorded and the
	 * measured floor at {@code 0.0}. The task files are copied rather than restated,
	 * because the launcher scores against them.
	 * @return the directory to point the launcher at.
	 */
	private Path evidenceTree() {
		Path resources = this.dir.resolve("resources");
		Path tasks = resources.resolve(Referee.TASKS);
		try {
			Files.createDirectories(tasks);
			try (var checkedIn = Files.list(OuterLoopFixtures.resources().resolve(Referee.TASKS))) {
				for (Path file : checkedIn.toList()) {
					if (file.getFileName().toString().endsWith(".json")) {
						Files.copy(file, tasks.resolve(file.getFileName()));
					}
				}
			}
			Map<String, Object> baseline = new LinkedHashMap<>();
			baseline.put("baselineScore", OuterLoopFixtures.scoreOf());
			baseline.put("headroom", true);
			baseline.put("verdict",
					"2 of 6 frozen tasks fail the primary baseline, so the outer loop has room" + " to move.");
			EvidenceWriter.write(baseline, resources.resolve(Referee.BASELINE));
			EvidenceWriter.write(Map.of("repeats", 3, "gateFloor", 0.0), resources.resolve(Referee.NOISE_FLOOR));
		}
		catch (IOException ex) {
			throw new UncheckedIOException("cannot stage the evidence tree the launcher reads", ex);
		}
		return resources;
	}

}
