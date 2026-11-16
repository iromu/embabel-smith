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

/**
 * Offline tests for the two eval arms (task 2.1): a fake model records every call, so the
 * run can be checked to exercise each scenario once with the skill in context and once
 * without it — the only difference between the arms being the skill itself, which is what
 * makes the pass-rate difference attributable to it.
 *
 * @author Ivan Rodriguez
 */
class EvalRunnerTest {

	private static final String WITH_MARKER = "WITH-ARM";

	private static final String WITH_PROMPT = WITH_MARKER + "\n{skill}";

	private static final String BASELINE_PROMPT = "BASELINE-ARM";

	private static final String GRADE_PROMPT = "GRADE";

	private static final String SKILL_BODY = "## Workflow\n1. Read the temperature record before accepting.";

	private final List<String> systems = new ArrayList<>();

	private final List<String> users = new ArrayList<>();

	private static EvalSuite suite() {
		return new EvalSuite("cold-store-goods-receipt",
				List.of(new EvalSuite.Eval(1, "receive a chilled pallet", "accepted or rejected", List.of(),
						List.of("must apply FEFO", "must record the temperature")),
						new EvalSuite.Eval(2, "a delivery arrives 3C warm", "rejected and escalated", List.of(),
								List.of("must reject the breach", "must escalate to quality"))));
	}

	private static Skill skill() {
		return new Skill("cold-store-goods-receipt", "handles chilled goods receipts", SKILL_BODY);
	}

	private static String judgeAnswer(boolean passed) {
		return "{\"expectations\": [{\"text\": \"a\", \"passed\": " + passed + ", \"evidence\": \"e\"},"
				+ " {\"text\": \"b\", \"passed\": " + passed + ", \"evidence\": \"e\"}]}";
	}

	/**
	 * The model for both arms and the judge: the arm is told apart by its system prompt,
	 * and the judge is told which arm it is grading by the marker in the answer it is
	 * shown.
	 */
	private static SpecPromptCaller armAwareModel() {
		return (system, user) -> {
			if (system.startsWith(GRADE_PROMPT)) {
				return judgeAnswer(user.contains("PASS"));
			}
			return system.startsWith(WITH_MARKER) ? "PASS the with-skill answer" : "FAIL the baseline answer";
		};
	}

	private EvalRunner recordingRunner(SpecPromptCaller model) {
		SpecPromptCaller recording = (system, user) -> {
			this.systems.add(system);
			this.users.add(user);
			return model.call(system, user);
		};
		return new EvalRunner(recording, new SkillGrader(recording, GRADE_PROMPT), WITH_PROMPT, BASELINE_PROMPT);
	}

	@Test
	void everyScenarioIsRunOnceWithTheSkillAndOnceWithout() {
		List<EvalRunner.EvalRun> runs = recordingRunner(armAwareModel()).run(skill(), suite());

		assertThat(runs).hasSize(4);
		assertThat(runs).extracting(EvalRunner.EvalRun::config)
			.containsExactly(EvalRunner.WITH_SKILL, EvalRunner.WITHOUT_SKILL, EvalRunner.WITH_SKILL,
					EvalRunner.WITHOUT_SKILL);
		assertThat(runs).extracting((run) -> run.eval().id()).containsExactly(1, 1, 2, 2);
	}

	@Test
	void onlyTheWithSkillArmCarriesTheSkillInTheSystemPrompt() {
		recordingRunner(armAwareModel()).run(skill(), suite());

		// Call order per eval: with-arm answer, its grading, baseline answer, its
		// grading.
		assertThat(this.systems.get(0)).startsWith("WITH-ARM")
			.contains("name: cold-store-goods-receipt")
			.contains("description: \"handles chilled goods receipts\"")
			.contains(SKILL_BODY);
		assertThat(this.systems.get(1)).isEqualTo(GRADE_PROMPT);
		assertThat(this.systems.get(2)).isEqualTo(BASELINE_PROMPT)
			.doesNotContain("cold-store-goods-receipt")
			.doesNotContain(SKILL_BODY);
		assertThat(this.systems.get(3)).isEqualTo(GRADE_PROMPT);
	}

	@Test
	void bothArmsAreAskedTheSameScenarioAndTheirAnswersAreKept() {
		List<EvalRunner.EvalRun> runs = recordingRunner(armAwareModel()).run(skill(), suite());

		assertThat(runs.get(0).answer()).isEqualTo("PASS the with-skill answer");
		assertThat(runs.get(1).answer()).isEqualTo("FAIL the baseline answer");
		assertThat(runs.get(0).eval().prompt()).isEqualTo(runs.get(1).eval().prompt());
		assertThat(this.users.get(0)).isEqualTo("receive a chilled pallet");
		assertThat(this.users.get(2)).isEqualTo("receive a chilled pallet");
	}

	@Test
	void eachArmIsGradedAgainstItsOwnAnswer() {
		// The gradings must not be shared between the arms, or the lift would always read
		// zero.
		List<EvalRunner.EvalRun> runs = recordingRunner(armAwareModel()).run(skill(), suite());

		assertThat(runs.get(0).grading().passed()).isEqualTo(2);
		assertThat(runs.get(1).grading().passed()).isZero();
		assertThat(Benchmark.aggregate(runs).lift()).isEqualTo(1.0);
	}

}
