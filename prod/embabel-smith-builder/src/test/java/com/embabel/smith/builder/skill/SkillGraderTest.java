/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.skill;

import com.embabel.smith.builder.core.SpecPromptCaller;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Offline tests for the LLM-as-judge grader (task 2.2): a canned judge answer is injected
 * through the {@link SpecPromptCaller} seam and the grading is checked — one verdict per
 * expectation, in the expectation's order — with no model and no endpoint needed.
 *
 * @author Ivan Rodriguez
 */
class SkillGraderTest {

	private final List<String> systems = new ArrayList<>();

	private final List<String> users = new ArrayList<>();

	private SkillGrader grader(String answer) {
		SpecPromptCaller caller = (system, user) -> {
			this.systems.add(system);
			this.users.add(user);
			return answer;
		};
		return new SkillGrader(caller, "You are a strict grader");
	}

	private static String judgeAnswer(boolean first, boolean second) {
		return "{\"expectations\": [" + "{\"text\": \"must apply FEFO\", \"passed\": " + first
				+ ", \"evidence\": \"the answer rotates stock\"}, "
				+ "{\"text\": \"must escalate a breach\", \"passed\": " + second
				+ ", \"evidence\": \"no escalation mentioned\"}]}";
	}

	@Test
	void gradesEachExpectationAgainstTheAnswer() {
		SkillGrader.Grading grading = grader(judgeAnswer(true, false)).grade("receive a chilled pallet",
				"rotate stock by date", List.of("must apply FEFO", "must escalate a breach"));

		assertThat(grading.total()).isEqualTo(2);
		assertThat(grading.passed()).isEqualTo(1);
		assertThat(grading.expectations()).extracting(SkillGrader.GradedExpectation::text)
			.containsExactly("must apply FEFO", "must escalate a breach");
		assertThat(grading.expectations().get(0).passed()).isTrue();
		assertThat(grading.expectations().get(0).evidence()).isEqualTo("the answer rotates stock");
		assertThat(grading.expectations().get(1).passed()).isFalse();
	}

	@Test
	void theJudgeSeesTheTaskTheAnswerAndEveryExpectation() {
		grader(judgeAnswer(true, true)).grade("receive a chilled pallet", "the answer text", List.of("a", "b", "c"));

		assertThat(this.systems).containsExactly("You are a strict grader");
		assertThat(this.users).hasSize(1);
		assertThat(this.users.get(0)).contains("<task>\nreceive a chilled pallet\n</task>")
			.contains("<answer>\nthe answer text\n</answer>")
			.contains("<expectations>\n1. a")
			.contains("3. c\n</expectations>");
	}

	@Test
	void anExpectationTheJudgeOmitsCountsAsFailed() {
		// The judge returned one entry for three expectations — the missing two must not
		// be
		// counted as passes, or a truncated answer would inflate the lift.
		String truncated = "{\"expectations\": [{\"text\": \"a\", \"passed\": true, \"evidence\": \"yes\"}]}";

		SkillGrader.Grading grading = grader(truncated).grade("t", "answer", List.of("a", "b", "c"));

		assertThat(grading.total()).isEqualTo(3);
		assertThat(grading.passed()).isEqualTo(1);
		assertThat(grading.expectations().get(1).passed()).isFalse();
		assertThat(grading.expectations().get(1).evidence()).isEqualTo("not graded");
	}

	@Test
	void aMissingExpectationsArrayCountsEverythingAsFailed() {
		SkillGrader.Grading grading = grader("{\"verdict\": \"no\"}").grade("t", "answer", List.of("a", "b"));

		assertThat(grading.total()).isEqualTo(2);
		assertThat(grading.passed()).isZero();
	}

	@Test
	void fencesAndProseAroundTheJudgementAreStripped() {
		String fenced = "Judgement:\n```json\n" + judgeAnswer(true, true) + "\n```";

		SkillGrader.Grading grading = grader(fenced).grade("t", "answer",
				List.of("must apply FEFO", "must escalate a breach"));

		assertThat(grading.passed()).isEqualTo(2);
	}

	@Test
	void anAnswerWithNoJsonAtAllFailsRatherThanSilentlyPassing() {
		assertThatThrownBy(() -> grader("the answer looks fine to me").grade("t", "answer", List.of("a")))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Failed to obtain the grading");
	}

}
