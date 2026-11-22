/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.baseline;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.embabel.smith.selfimprove.innerloop.ModelCallMeter;
import com.embabel.smith.selfimprove.referee.FrozenTaskSet;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Task 6.2: arm agreement is a decision, not a footnote. A task the two arms disagree
 * about is excluded from the deciding score and named by id, so the smaller denominator
 * is visible in the record rather than absorbed into a number one arm decided.
 *
 * <p>
 * The case that proves the rule is real is the one where the excluded task is a task the
 * primary arm <em>passed</em> and the label rejected: dropping it must move the deciding
 * number, not merely annotate it. Delete the exclusion — pass {@code excludedTaskIds}
 * empty and the primary score through as the deciding score — and both the score
 * assertion and the id-list assertion below fail. The last two tests pin what makes the
 * rule unfakeable: a hand-picked exclusion is refused by the record itself, and a
 * denominator the disagreement emptied entirely is a named refusal rather than a zero.
 *
 * <p>
 * No model is called; the answers come from {@link BaselineFixtures} through the same
 * seam the live run uses.
 *
 * @author Ivan Rodriguez
 */
class DivergingTaskExclusionTest {

	private static final FrozenTaskSet TASKS = BaselineFixtures.frozen();

	@Test
	void aDisagreementMovesTheDecidingScoreAndNamesTheTaskExcludedToDoIt() {
		Baseline agreed = baselineWith(BaselineFixtures.PRIORS);
		Map<String, String> split = new LinkedHashMap<>(BaselineFixtures.PRIORS);
		split.put("b27-t04", BaselineFixtures.GATE_PASSES_BUT_THE_FULL_CHECK_DOES);
		Baseline disagreeing = baselineWith(split);

		// The control. Without it the assertions below could be satisfied by a rule that
		// always excludes something.
		assertThat(agreed.excludedTaskIds()).as("both arms agreed on every task here").isEmpty();
		assertThat(agreed.decidingTaskIds()).containsExactlyElementsOf(TASKS.ids());
		assertThat(agreed.decidingScore()).isEqualTo(agreed.baselineScore());
		assertThat(agreed.passMarkForCandidates()).isEqualTo(agreed.baselineScore());

		assertThat(disagreeing.baselineScore()).as("the rule does not touch the primary arm's own number")
			.isCloseTo(2.0 / 6.0, within(1e-9));
		assertThat(disagreeing.humanLabelFailingTaskIds()).contains("b27-t04");
		assertThat(disagreeing.divergingTaskIds()).as("the divergence the existing field already records")
			.containsExactly("b27-t04");
		assertThat(disagreeing.excludedTaskIds()).as("is the same set the deciding score excludes")
			.containsExactly("b27-t04");
		assertThat(disagreeing.decidingTaskIds()).containsExactly("b27-t01", "b27-t02", "b27-t03", "b27-t05",
				"b27-t06");
		assertThat(disagreeing.decidingScore())
			.as("four of the five surviving tasks fail the primary arm, so one of its two passes was the task the"
					+ " label rejected")
			.isCloseTo(1.0 / 5.0, within(1e-9))
			.isNotEqualTo(disagreeing.baselineScore());
		assertThat(disagreeing.decidingScore()).as("the same set without the disagreement scored differently")
			.isNotEqualTo(agreed.decidingScore());
		assertThat(disagreeing.passMarkForCandidates()).isEqualTo(disagreeing.decidingScore());
		assertThat(disagreeing.verdict()).contains("DECIDING SCORE").contains("b27-t04").contains("5 of 6");
	}

	@Test
	void aTaskBothArmsAgreedOnCannotBeExcludedByHand() {
		List<String> ids = TASKS.ids();
		// Both arms reject b27-t02, so they agree on it and nothing licenses dropping it.
		List<String> bothFail = List.of("b27-t02");
		List<String> handPickedDeciding = ids.stream().filter((id) -> !id.equals("b27-t02")).toList();

		assertThatThrownBy(() -> new Baseline(ids, List.of(), Baseline.PRIMARY_IS, 5.0 / 6.0, bothFail,
				Baseline.HUMAN_LABEL_IS, 5.0 / 6.0, bothFail, List.of(), List.of("b27-t02"), handPickedDeciding, 1.0,
				ids.size(), true, "hand-built"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("inconvenient")
			.hasMessageContaining("b27-t02");
	}

	@Test
	void disagreementOnEveryTaskIsANamedRefusalAndNotADenominatorOfZero() {
		List<String> ids = TASKS.ids();

		Baseline splitOnEverything = new Baseline(ids, List.of(), Baseline.PRIMARY_IS, 1.0, List.of(),
				Baseline.HUMAN_LABEL_IS, 0.0, ids, ids, ids, List.of(), null, ids.size(), false, "hand-built");

		assertThat(splitOnEverything.decidingScore()).as("no denominator, recorded as none rather than as zero")
			.isNull();
		assertThatThrownBy(splitOnEverything::passMarkForCandidates).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("no deciding score");
	}

	private static Baseline baselineWith(Map<String, String> answers) {
		ModelCallMeter meter = new ModelCallMeter();
		var scores = new BaselineRun(TASKS, BaselineFixtures.canned(TASKS, answers), meter).measure();
		return Baseline.of(TASKS, scores, meter.count());
	}

}
