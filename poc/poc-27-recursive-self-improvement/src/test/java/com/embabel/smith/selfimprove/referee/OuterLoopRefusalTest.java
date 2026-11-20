/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.referee;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Task 3.4: the paid outer loop refuses to start while the baseline it would compare
 * against has not been recorded, and the refusal names the artifact that is missing.
 *
 * <p>
 * This is the path that exists today. Tasks 2.4 and 2.5 have not run, so neither
 * {@code evidence/baseline.json} nor {@code evidence/noise-floor.json} is in the checkout
 * — the second is named by {@code evidence/MUTABILITY.md} as fixed for the run, and the
 * first is where this referee expects 2.4's measurement to land, because 2.4 has not
 * named a path of its own. Nothing here fabricates either file to get past the refusal;
 * the second test writes them into a temporary copy only to show the refusal is caused by
 * their absence and not by the referee being unable to run.
 *
 * @author Ivan Rodriguez
 */
class OuterLoopRefusalTest {

	@Test
	void theOuterLoopRefusesWhileNeitherArtifactIsRecorded() {
		Referee referee = Referee.start(RefereeFixtures.resources(), null,
				new RecordingInvokerFactory(RefereeFixtures.frozenTasks(), RefereeFixtures.CORRECT));

		assertThatThrownBy(referee::assertOuterLoopMayStart).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("may not start")
			.hasMessageContaining(Referee.BASELINE.toString())
			.hasMessageContaining(Referee.NOISE_FLOOR.toString())
			.hasMessageContaining("2.4");
	}

	@Test
	void theRefusalIsCausedByAbsenceAndNotByTheRefereeBeingUnableToRun(@TempDir Path resources) throws IOException {
		Path tasks = Files.createDirectories(resources.resolve("benchmark/tasks"));
		try (var files = Files.list(RefereeFixtures.resources().resolve("benchmark/tasks"))) {
			for (Path file : files.toList()) {
				Files.copy(file, tasks.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
			}
		}
		Files.createDirectories(resources.resolve("evidence"));
		// Presence is the check, so the content is a placeholder. What is being asserted
		// is that the
		// refusal lifts when the artifacts exist — not that their contents would satisfy
		// 2.4 or 2.5.
		Files.writeString(resources.resolve(Referee.BASELINE), "{}");
		Files.writeString(resources.resolve(Referee.NOISE_FLOOR), "{}");

		Referee referee = Referee.start(resources, null,
				new RecordingInvokerFactory(RefereeFixtures.frozenTasks(), RefereeFixtures.CORRECT));

		assertThatCode(referee::assertOuterLoopMayStart).doesNotThrowAnyException();
	}

	@Test
	void aNoiseFloorWithoutABaselineStillRefusesAndNamesOnlyTheBaseline(@TempDir Path resources) throws IOException {
		Path tasks = Files.createDirectories(resources.resolve("benchmark/tasks"));
		try (var files = Files.list(RefereeFixtures.resources().resolve("benchmark/tasks"))) {
			for (Path file : files.toList()) {
				Files.copy(file, tasks.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
			}
		}
		Files.createDirectories(resources.resolve("evidence"));
		Files.writeString(resources.resolve(Referee.NOISE_FLOOR), "{}");

		Referee referee = Referee.start(resources, null,
				new RecordingInvokerFactory(RefereeFixtures.frozenTasks(), RefereeFixtures.CORRECT));

		assertThatThrownBy(referee::assertOuterLoopMayStart).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining(Referee.BASELINE.toString())
			.hasMessageNotContaining("looked for at " + resources.resolve(Referee.NOISE_FLOOR));
	}

}
