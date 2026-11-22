/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.referee;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 3.3: the task set is read-only to the loop. A candidate may emit evaluation tasks,
 * but the referee scores the frozen set and nothing else, and records what it dropped by
 * identifier so the exclusion is evidence rather than an assumption.
 *
 * @author Ivan Rodriguez
 */
class TaskSetReadOnlyToLoopTest {

	private final FrozenTaskSet frozen = RefereeFixtures.frozenTasks();

	@TempDir
	Path work;

	@Test
	void proposedTasksAreExcludedByNameAndNeverPutToTheCandidate() throws IOException {
		// Both proposals are written to be trivially passable. If they reached the scorer
		// they would
		// buy points, which is the exact move this task closes: a candidate that can add
		// a task can
		// choose which work counts.
		Path proposed = Files.createDirectories(this.work.resolve("proposed-tasks"));
		Files.writeString(proposed.resolve("b27-x01-easy.json"), task("b27-x01-easy", "What is two plus two?", "yes"));
		Files.writeString(proposed.resolve("b27-x02-echo.json"),
				task("b27-x02-echo", "Repeat the phrase trivially.", "trivially"));

		RecordingInvokerFactory factory = new RecordingInvokerFactory(this.frozen, RefereeFixtures.CORRECT);
		Referee referee = Referee.start(RefereeFixtures.resources(), null, factory);
		Candidate candidate = new Candidate("proposes-its-own-tasks", RefereeFixtures.validPack("proposer"), proposed);

		RefereeReport report = referee.score(candidate, 0.0);

		assertThat(report.excludedProposedTaskIds()).containsExactly("b27-x01-easy", "b27-x02-echo");
		assertThat(report.scoredTaskIds()).isEqualTo(this.frozen.ids());
		assertThat(factory.asked()).doesNotContain("What is two plus two?", "Repeat the phrase trivially.");
		// The denominator stayed the frozen six, so the two free points could not dilute
		// or inflate it.
		assertThat(report.frozenScore()).isEqualTo(1.0);
	}

	@Test
	void aCandidateThatProposesNothingRecordsNoExclusion() {
		Referee referee = Referee.start(RefereeFixtures.resources(), null,
				new RecordingInvokerFactory(this.frozen, RefereeFixtures.CORRECT));

		RefereeReport report = referee.score(RefereeFixtures.candidate("quiet"), 0.0);

		assertThat(report.excludedProposedTaskIds()).isEmpty();
	}

	@Test
	void anUnreadableProposalIsStillRecorded() throws IOException {
		// A proposal is excluded, not validated. A file that is not even a task must not
		// become a
		// scoring failure or a crash, and it must still be named in the record.
		Path proposed = Files.createDirectories(this.work.resolve("proposed-tasks"));
		Files.writeString(proposed.resolve("b27-broken.json"), "this is not JSON");

		Referee referee = Referee.start(RefereeFixtures.resources(), null,
				new RecordingInvokerFactory(this.frozen, RefereeFixtures.CORRECT));
		Candidate candidate = new Candidate("malformed-proposer", RefereeFixtures.validPack("malformed"), proposed);

		RefereeReport report = referee.score(candidate, 0.0);

		assertThat(report.excludedProposedTaskIds()).containsExactly("b27-broken.json");
		assertThat(report.scoredTaskIds()).isEqualTo(this.frozen.ids());
	}

	@Test
	void scoringLeavesTheFrozenDirectoryAsItFoundIt() throws IOException {
		// Read-only means read-only: a referee that normalized, re-ordered or rewrote a
		// task file
		// would be editing the artifact it is judged by, so the directory is snapshotted
		// before
		// scoring and compared afterwards.
		Path tasks = RefereeFixtures.resources().resolve("benchmark/tasks");
		Map<String, Long> before = snapshot(tasks);

		Referee referee = Referee.start(RefereeFixtures.resources(), null,
				new RecordingInvokerFactory(this.frozen, RefereeFixtures.PRIORS));
		referee.score(RefereeFixtures.candidate("scorer"), 0.0);
		referee.score(RefereeFixtures.candidate("scorer"), 0.0);

		// Six counts the tasks, not the files: the directory also carries a README
		// documenting the format, which is not a task and must not be scored.
		assertThat(before.keySet()).filteredOn((name) -> name.endsWith(".json")).hasSize(6);
		assertThat(snapshot(tasks)).isEqualTo(before);
	}

	private static Map<String, Long> snapshot(Path dir) throws IOException {
		Map<String, Long> files = new TreeMap<>();
		try (var paths = Files.list(dir)) {
			for (Path file : paths.toList()) {
				files.put(file.getFileName().toString(), Files.size(file));
			}
		}
		return Map.copyOf(files);
	}

	private static String task(String id, String prompt, String required) {
		return """
				{
				  "id": "%s",
				  "stage": "candidate proposal",
				  "prompt": "%s",
				  "require": ["%s"],
				  "anyOf": [],
				  "forbid": [],
				  "check": "Authored by the candidate, which is why it is excluded."
				}
				""".formatted(id, prompt, required);
	}

}
