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
 * The absence is simulated here, never borrowed from the checkout. Tasks 2.4 and 2.5 have
 * since run, and both {@code evidence/baseline.json} and
 * {@code evidence/noise-floor.json} are recorded in the real resources directory, so a
 * test that pointed there and asserted they were missing would pass only for as long as
 * the measurement it exists to guard had not been committed — which is the moment it
 * should stop passing. Every test below therefore builds its own resources tree and
 * decides for itself whether the two artifacts are inside it, so the refusal under test
 * is a function of that directory alone.
 *
 * @author Ivan Rodriguez
 */
class OuterLoopRefusalTest {

	@Test
	void theOuterLoopRefusesWhileNeitherArtifactIsRecorded(@TempDir Path resources) throws IOException {
		copyFrozenTasksInto(resources);
		// The directory is there and the artifacts are not, so this asserts the refusal
		// is
		// caused by the two files being unrecorded rather than by the directory being
		// absent.
		Files.createDirectories(resources.resolve("evidence"));

		Referee referee = Referee.start(resources, null,
				new RecordingInvokerFactory(RefereeFixtures.frozenTasks(), RefereeFixtures.CORRECT));

		assertThatThrownBy(referee::assertOuterLoopMayStart).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("may not start")
			.hasMessageContaining(Referee.BASELINE.toString())
			.hasMessageContaining(Referee.NOISE_FLOOR.toString())
			.hasMessageContaining("2.4");
	}

	@Test
	void theRefusalIsCausedByAbsenceAndNotByTheRefereeBeingUnableToRun(@TempDir Path resources) throws IOException {
		copyFrozenTasksInto(resources);
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
		copyFrozenTasksInto(resources);
		Files.createDirectories(resources.resolve("evidence"));
		Files.writeString(resources.resolve(Referee.NOISE_FLOOR), "{}");

		Referee referee = Referee.start(resources, null,
				new RecordingInvokerFactory(RefereeFixtures.frozenTasks(), RefereeFixtures.CORRECT));

		assertThatThrownBy(referee::assertOuterLoopMayStart).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining(Referee.BASELINE.toString())
			.hasMessageNotContaining("looked for at " + resources.resolve(Referee.NOISE_FLOOR));
	}

	private static void copyFrozenTasksInto(Path resources) throws IOException {
		Path tasks = Files.createDirectories(resources.resolve("benchmark/tasks"));
		try (var files = Files.list(RefereeFixtures.resources().resolve("benchmark/tasks"))) {
			for (Path file : files.toList()) {
				Files.copy(file, tasks.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}

}
