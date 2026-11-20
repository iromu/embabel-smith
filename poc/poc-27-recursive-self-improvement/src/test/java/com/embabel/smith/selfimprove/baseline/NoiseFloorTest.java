/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.baseline;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.embabel.smith.builder.core.SpecPromptCaller;
import com.embabel.smith.selfimprove.innerloop.ModelCallMeter;
import com.embabel.smith.selfimprove.referee.FrozenTaskSet;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Task 2.5 offline: the repeat-to-repeat floor is the spread of a live model's answers,
 * and it is computed both when the answers move and when they do not.
 *
 * <p>
 * The varying sets are what make this non-vacuous. A floor computed only from repeats
 * that agreed would be indistinguishable from a calculation that always returns zero, and
 * a zero is exactly the number task 7.4 has to be able to tell a real floor from.
 *
 * @author Ivan Rodriguez
 */
class NoiseFloorTest {

	private static final FrozenTaskSet TASKS = BaselineFixtures.frozen();

	@Test
	void answersThatDoNotMoveAcrossRepeatsGiveAZeroFloorAndSayItIsZero() {
		NoiseFloor floor = floorOf(3, BaselineFixtures.canned(TASKS, BaselineFixtures.PRIORS));

		assertThat(floor.repeats()).isEqualTo(NoiseFloor.MIN_REPEATS);
		assertThat(floor.gateFloor()).isZero();
		assertThat(floor.humanLabelFloor()).isZero();
		assertThat(floor.gateFlippingTaskIds()).isEmpty();
		assertThat(floor.humanLabelFlippingTaskIds()).isEmpty();
		assertThat(floor.answersMoved()).isFalse();
		// The honest zero, in the file: not raised for safety, and with the consequence
		// for
		// task 7.4 stated where a reader of the number will meet it.
		assertThat(floor.measures()).contains("answer variance across repeats")
			.contains("not scorer variance")
			.contains("reported as 0.0, not raised");
		// And the file says which of its two spreads is the threshold, so nobody can gate
		// a
		// substring delta against the label's spread.
		assertThat(floor.primaryFloorIs()).contains("gateFloor").contains("only threshold").contains("unit mismatch");
	}

	@Test
	void answersThatMoveAcrossRepeatsGiveTheObservedSpreadAndNameWhatMoved() {
		NoiseFloor floor = floorOf(3, BaselineFixtures.perPass(TASKS,
				List.of(BaselineFixtures.PRIORS, BaselineFixtures.CORRECT, BaselineFixtures.PRIORS)));

		assertThat(floor.gateScores()).hasSize(NoiseFloor.MIN_REPEATS);
		assertThat(floor.gateScores().get(1)).as("the middle pass answered everything correctly")
			.isCloseTo(1.0, within(1e-9));
		assertThat(floor.gateFloor()).isCloseTo(4.0 / 6.0, within(1e-9));
		assertThat(floor.humanLabelFloor()).isCloseTo(4.0 / 6.0, within(1e-9));
		assertThat(floor.gateFlippingTaskIds()).containsExactly("b27-t01", "b27-t02", "b27-t03", "b27-t06");
		assertThat(floor.humanLabelFlippingTaskIds()).containsExactly("b27-t01", "b27-t02", "b27-t03", "b27-t06");
		assertThat(floor.answersMoved()).isTrue();
	}

	@Test
	void movementThePrimaryScorerCannotSeeIsStillRecordedAndStillNotAThreshold() {
		Map<String, String> unchanged = BaselineFixtures.PRIORS;
		Map<String, String> drifted = new LinkedHashMap<>(BaselineFixtures.PRIORS);
		drifted.put("b27-t04", BaselineFixtures.GATE_PASSES_BUT_THE_FULL_CHECK_DOES);

		// b27-t04 passes the substring scorer whichever way it is answered, so the
		// primary
		// floor is zero while the human label's spread is not. The record keeps both and
		// names what moved under each — and the zero primary floor stays the threshold,
		// because that is the scorer the delta will be a difference of.
		NoiseFloor floor = floorOf(3, BaselineFixtures.perPass(TASKS, List.of(unchanged, drifted, unchanged)));

		assertThat(floor.gateFloor()).as("the primary score never moved").isZero();
		assertThat(floor.gateFlippingTaskIds()).isEmpty();
		assertThat(floor.answersMoved()).as("the label moved; the primary scorer did not").isFalse();
		assertThat(floor.humanLabelFloor()).isCloseTo(1.0 / 6.0, within(1e-9));
		assertThat(floor.humanLabelFlippingTaskIds()).containsExactly("b27-t04");
	}

	@Test
	void fewerRepeatsThanTheFloorClaimsIsRefusedRatherThanRecorded() {
		List<ScoredAnswers> two = new BaselineRun(TASKS, BaselineFixtures.canned(TASKS, BaselineFixtures.PRIORS),
				new ModelCallMeter())
			.repeats(2);

		assertThatThrownBy(() -> NoiseFloor.of(TASKS, two)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("at least 3 repeats")
			.hasMessageContaining("2 were scored");
		// The positive control: the same reduction over one more pass is accepted, so the
		// refusal above is about the count and not about the answers.
		assertThat(floorOf(NoiseFloor.MIN_REPEATS, BaselineFixtures.canned(TASKS, BaselineFixtures.PRIORS)).repeats())
			.isEqualTo(NoiseFloor.MIN_REPEATS);
	}

	@Test
	void everyRepeatAsksEveryFrozenPromptOnceAndNoMore() {
		ModelCallMeter meter = new ModelCallMeter();

		new BaselineRun(TASKS, BaselineFixtures.canned(TASKS, BaselineFixtures.PRIORS), meter)
			.repeats(NoiseFloor.MIN_REPEATS);

		// The budget the launcher declares before the first call, checked against the
		// counter it is declared from.
		assertThat(meter.count()).isEqualTo(TASKS.ids().size() * NoiseFloor.MIN_REPEATS);
	}

	private static NoiseFloor floorOf(int repeats, SpecPromptCaller answers) {
		return NoiseFloor.of(TASKS, new BaselineRun(TASKS, answers, new ModelCallMeter()).repeats(repeats));
	}

}
