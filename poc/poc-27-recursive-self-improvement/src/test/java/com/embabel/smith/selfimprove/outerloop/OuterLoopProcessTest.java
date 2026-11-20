/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.outerloop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.data.Offset.offset;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.embabel.smith.builder.eval.InvokerFactory;
import com.embabel.smith.selfimprove.baseline.EvidenceWriter;
import com.embabel.smith.selfimprove.innerloop.PolicyGenerator.MutationOperator;
import com.embabel.smith.selfimprove.innerloop.PolicyPrompt;
import com.embabel.smith.selfimprove.referee.FrozenTaskSet;
import com.embabel.smith.selfimprove.referee.Referee;

/**
 * Task 10.2's launcher, offline: its decision logic and its three outputs, with canned
 * improvers and a canned answer table. See {@link OuterLoopFixtures} for what a score
 * produced this way does and does not mean — it measures the loop, never a model.
 *
 * <p>
 * Every artifact the launcher reads is built in a temp directory, including the frozen
 * task files it needs to stand a referee up. The checked-in {@code src/main/resources}
 * tree now genuinely holds a baseline recording headroom and a measured floor, so a test
 * that read it could only ever see the permissive case, and the case this class exists to
 * catch is the restrictive one.
 *
 * @author Ivan Rodriguez
 */
class OuterLoopProcessTest {

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
	Path dir;

	@Test
	void refusesARecordedBaselineThatSaysThereIsNoHeadroom() throws IOException {
		// The case the referee's guard misses, and the only test that catches it: it
		// checks that the two artifacts are regular files, so a baseline recording that
		// the loop cannot move anything waves a paid run through. The control below is
		// the point — it shows the referee accepting the very tree this test refuses.
		Path resources = evidenceTree(false, 0.0);

		assertThat(OuterLoopProcess.refusalReason(resources)).contains("headroom: false").contains("cannot run");
		assertThatCode(() -> Referee.start(resources, null, noModelHere()).assertOuterLoopMayStart())
			.as("the referee's own check is for presence, not content")
			.doesNotThrowAnyException();
	}

	@Test
	void refusesWhenAnArtifactIsAbsent() throws IOException {
		Path resources = this.dir.resolve("baseline-only");
		EvidenceWriter.write(Map.of("baselineScore", OuterLoopFixtures.scoreOf(), "headroom", true, "verdict",
				"2 of 6 frozen tasks fail the primary baseline."), resources.resolve(Referee.BASELINE));

		assertThat(OuterLoopProcess.refusalReason(resources)).contains(Referee.NOISE_FLOOR.toString())
			.contains("not recorded");
	}

	@Test
	void permitsTheRunAndReadsTheRecordedValuesWithoutRaisingThem() throws IOException {
		// The measured floor is 0.0. Read back at 0.0, because a launcher that
		// substituted
		// a higher number "to be safe" would be moving the artifact MUTABILITY.md fixes.
		Path resources = evidenceTree(true, 0.0);

		assertThat(OuterLoopProcess.refusalReason(resources)).isNull();
		assertThat(OuterLoopProcess.recordedNoiseFloor(resources).delta()).isZero();
		assertThat(OuterLoopProcess.recordedNoiseFloor(resources).measured()).isTrue();
		assertThat(OuterLoopProcess.recordedBaseline(resources).passRate()).isEqualTo(OuterLoopFixtures.scoreOf(),
				offset(1e-9));
		assertThat(OuterLoopProcess.recordedBaseline(resources).measured()).isTrue();
	}

	@Test
	void onePassProducesTheArchiveTheGenerationReportsAndTheHeldCount() throws IOException {
		Path resources = evidenceTree(true, 0.0);
		FrozenTaskSet tasks = FrozenTaskSet.load(resources.resolve(Referee.TASKS));
		Path work = this.dir.resolve("work");

		OuterRun run = OuterLoopProcess.runPass(resources, OuterLoopFixtures.table(tasks, cannedRows()), work);

		// The budget arithmetic is exactly right, or a variant would go unscored and the
		// archive would be short by one.
		assertThat(run.notMeasured()).isEmpty();
		assertThat(run.generations()).hasSize(2);
		assertThat(run.archive().size()).isEqualTo(2 * (1 + OPERATORS.size()));
		assertThat(run.outcome().completedGenerations()).isEqualTo(2);

		assertThat(work.resolve(Archive.RECORD_FILE)).exists();
		assertThat(Files.readAllLines(work.resolve(Archive.RECORD_FILE))).hasSize(2 * (1 + OPERATORS.size()));

		Path reports = work.resolve(OuterLoopProcess.GENERATION_REPORT_FILE);
		assertThat(reports).exists();
		assertThat(Files.readAllLines(reports)).hasSize(2);
		assertThat(Files.readString(reports)).contains("b27-gen2");

		assertThat(run.heldIds()).isNotEmpty();
	}

	/**
	 * Two generations where generation 2's mutation is worth exactly one task more than
	 * the variant its improver was derived from, so both generations complete and at
	 * least one candidate is gated and held.
	 * @return the answer rows, keyed by the variant ids the improvers produced.
	 */
	private Map<String, Map<String, String>> cannedRows() {
		PolicyImprover gen1 = PolicyImprover.surviving(PolicyPrompt.frozenSeed());
		PolicyImprover gen2 = gen1
			.derive(OuterLoopFixtures.proposalFor(gen1.propose(OPERATORS), MutationOperator.SEED));
		Map<String, String[]> gen1Passing = Map.of("SEED", THREE, "KEYWORD_INSTEAD_OF_PREFIX", TWO, "DROP_EXCLUSION",
				ONE, "DROP_TRIGGER", ONE, "ADD_LATENCY_TRIGGER", ONE);
		Map<String, String[]> gen2Passing = Map.of("SEED", THREE, "KEYWORD_INSTEAD_OF_PREFIX", ALL, "DROP_EXCLUSION",
				ONE, "DROP_TRIGGER", ONE, "ADD_LATENCY_TRIGGER", ONE);
		Map<String, Map<String, String>> rows = new LinkedHashMap<>(
				OuterLoopFixtures.tableOf(gen1, OPERATORS, gen1Passing));
		rows.putAll(OuterLoopFixtures.tableOf(gen2, OPERATORS, gen2Passing));
		return rows;
	}

	/**
	 * A resources tree holding the frozen task files as checked in, plus the two
	 * artifacts with the values this test needs. The task files are copied, not restated:
	 * the referee scores against them, and a fixture that rewrote them would change the
	 * frozen set.
	 * @param headroom what {@code baseline.json} records about there being room to move
	 * @param gateFloor what {@code noise-floor.json} records as the measured floor
	 * @return the directory to point the launcher at.
	 */
	private Path evidenceTree(boolean headroom, double gateFloor) throws IOException {
		Path resources = this.dir.resolve("resources");
		Path tasks = resources.resolve(Referee.TASKS);
		Files.createDirectories(tasks);
		try (var checkedIn = Files.list(OuterLoopFixtures.resources().resolve(Referee.TASKS))) {
			for (Path file : checkedIn.toList()) {
				Files.copy(file, tasks.resolve(file.getFileName()));
			}
		}
		Path evidence = resources.resolve("evidence");
		Files.createDirectories(evidence);
		Map<String, Object> baseline = new LinkedHashMap<>();
		baseline.put("baselineScore", OuterLoopFixtures.scoreOf());
		baseline.put("headroom", headroom);
		baseline.put("verdict",
				headroom ? "2 of 6 frozen tasks fail the primary baseline, so the outer loop has room" + " to move."
						: "STOP — the outer loop cannot run.");
		// Referee.BASELINE and Referee.NOISE_FLOOR already carry their own `evidence/`
		// segment, so they resolve against the resources root. Resolving them against the
		// evidence directory instead writes to evidence/evidence/ and leaves the launcher
		// looking at an empty tree.
		EvidenceWriter.write(baseline, resources.resolve(Referee.BASELINE));
		EvidenceWriter.write(Map.of("repeats", 3, "gateFloor", gateFloor), resources.resolve(Referee.NOISE_FLOOR));
		return resources;
	}

	private static InvokerFactory noModelHere() {
		return (pack) -> (input) -> {
			throw new IllegalStateException("no model in an offline test");
		};
	}

}
