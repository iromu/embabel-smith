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
import java.util.Map;

import com.embabel.smith.selfimprove.referee.FrozenTaskSet;
import com.embabel.smith.selfimprove.referee.Referee;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Tasks 2.4 and 2.5 end to end, offline: the launcher's recording step writes the two
 * artifacts, and the referee — which is what consumes them — accepts or refuses the run
 * based on what was written.
 *
 * <p>
 * This is where the two tasks meet the section that depends on them. Task 3.4 left the
 * naming of these paths open ("when 2.4 lands it either writes here or this constant
 * changes"), and 2.4 closed the question by landing at exactly the referee's path — so
 * the constant never had to move, which these tests also pin by writing to the referee's
 * own constants and handing it the directory back. No model is called.
 *
 * @author Ivan Rodriguez
 */
class BaselineEvidenceTest {

	@TempDir
	Path work;

	private final FrozenTaskSet tasks = BaselineFixtures.frozen();

	@Test
	void aBaselineWithHeadroomRecordsBothArtifactsAndLetsTheRefereeStart() {
		Path resources = BaselineFixtures.resourcesWithFrozenTasks(this.work);

		int exitCode = BaselineProcess.recordEvidence(this.tasks,
				BaselineFixtures.canned(this.tasks, BaselineFixtures.PRIORS), resources);

		assertThat(exitCode).isZero();
		assertThat(resources.resolve(Referee.BASELINE)).isRegularFile();
		assertThat(resources.resolve(Referee.NOISE_FLOOR)).isRegularFile();
		// The path task 2.5 names in the change, spelled the way the referee looks for
		// it.
		assertThat(Referee.NOISE_FLOOR).isEqualTo(Path.of("evidence", "noise-floor.json"));
		assertThatCode(() -> referee(resources).assertOuterLoopMayStart()).doesNotThrowAnyException();
	}

	@Test
	void aBaselineWithoutHeadroomStopsAndTheRefereeRefusesTheRunItCannotJustify() {
		Path resources = BaselineFixtures.resourcesWithFrozenTasks(this.work);

		int exitCode = BaselineProcess.recordEvidence(this.tasks,
				BaselineFixtures.canned(this.tasks, BaselineFixtures.CORRECT), resources);

		// The stop-and-record path, and the reason stopping early without writing the
		// floor
		// is the right thing: the referee's own presence check is then what keeps a loop
		// with
		// no headroom from starting later.
		assertThat(exitCode).as("no headroom is a non-zero outcome, not a silent success").isEqualTo(1);
		assertThat(resources.resolve(Referee.BASELINE)).as("the finding is recorded").isRegularFile();
		assertThat(resources.resolve(Referee.NOISE_FLOOR)).as("the remaining repeats were not spent").doesNotExist();
		assertThatThrownBy(() -> referee(resources).assertOuterLoopMayStart()).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("noise-floor.json");
	}

	@Test
	void theRecordedBaselineCarriesBothArmsAndSaysWhichOneTheGateUses() throws IOException {
		Map<String, String> answers = new LinkedHashMap<>(BaselineFixtures.CORRECT);
		answers.put("b27-t04", BaselineFixtures.GATE_PASSES_BUT_THE_FULL_CHECK_DOES);
		Path resources = BaselineFixtures.resourcesWithFrozenTasks(this.work);

		BaselineProcess.recordEvidence(this.tasks, BaselineFixtures.canned(this.tasks, answers), resources);

		JsonNode written = read(resources.resolve(Referee.BASELINE));
		assertThat(written.path("primaryBaselineIs").asText()).contains("EvalScorer").contains("7.4");
		assertThat(written.path("baselineScore").asDouble()).isCloseTo(1.0, within(1e-9));
		assertThat(written.path("humanLabelScore").asDouble()).isLessThan(1.0);
		assertThat(written.path("humanLabelFailingTaskIds").toString()).contains("b27-t04");
		assertThat(written.path("divergingTaskIds").toString()).as("the gap is in the file, not a footnote")
			.contains("b27-t04");
		assertThat(written.path("humanLabelIs").asText()).contains("known gap");
		assertThat(written.path("narrowedTaskIds").size()).isEqualTo(this.tasks.narrowedTaskIds().size());
		assertThat(written.path("taskIds").size()).isEqualTo(this.tasks.ids().size());
		// The cost of the run is evidence in the file, read off the meter rather than
		// typed
		// in: six prompts, and no repeats spent after the stop.
		assertThat(written.path("modelCalls").asInt()).isEqualTo(this.tasks.ids().size());
		assertThat(written.path("verdict").asText()).contains("STOP").contains("DIVERGENCE");
	}

	@Test
	void theRecordedNoiseFloorCarriesTheSpreadAndWhatItMeasures() throws IOException {
		Path resources = BaselineFixtures.resourcesWithFrozenTasks(this.work);

		BaselineProcess.recordEvidence(this.tasks, BaselineFixtures.canned(this.tasks, BaselineFixtures.PRIORS),
				resources);

		JsonNode written = read(resources.resolve(Referee.NOISE_FLOOR));
		assertThat(written.path("repeats").asInt()).isEqualTo(NoiseFloor.MIN_REPEATS);
		assertThat(written.path("gateScores").size()).isEqualTo(NoiseFloor.MIN_REPEATS);
		assertThat(written.path("humanLabelScores").size()).isEqualTo(NoiseFloor.MIN_REPEATS);
		assertThat(written.path("gateFloor").asDouble()).as("the primary floor is the threshold").isZero();
		assertThat(written.path("humanLabelFloor").asDouble()).isZero();
		assertThat(written.has("gateFlippingTaskIds")).as("present, not merely absent").isTrue();
		assertThat(written.path("gateFlippingTaskIds").size()).isZero();
		assertThat(written.path("primaryFloorIs").asText()).contains("gateFloor").contains("only threshold");
		assertThat(written.path("measures").asText()).contains("not scorer variance");
	}

	private Referee referee(Path resources) {
		return Referee.start(resources, null, (pack) -> (input) -> {
			throw new IllegalStateException("a baseline is a score of answers, not of a deployed pack");
		});
	}

	private static JsonNode read(Path file) {
		try {
			return new ObjectMapper().readTree(Files.readString(file));
		}
		catch (IOException ex) {
			throw new UncheckedIOException("cannot read the evidence just written to " + file, ex);
		}
	}

}
