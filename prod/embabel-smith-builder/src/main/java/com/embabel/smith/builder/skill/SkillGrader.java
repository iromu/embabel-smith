/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.skill;

import java.util.ArrayList;
import java.util.List;

import com.embabel.smith.builder.core.SpecPromptCaller;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The LLM-as-judge grader (the skill-creator's grader step). Given a run's answer and the
 * eval's verifiable expectations, it decides pass/fail for each, with a short piece of
 * evidence. The result is the skill-creator {@code grading.json} shape: an
 * {@code expectations} array of {@code {text, passed, evidence}}.
 *
 * <p>
 * This is the skill path's departure from the spec path's deterministic
 * {@link com.embabel.smith.builder.eval.EvalScorer}: the gate is a relative lift rather
 * than an exact score, precisely because the judge is not deterministic.
 *
 * @author Ivan Rodriguez
 */
public class SkillGrader {

	private final SpecPromptCaller caller;

	private final String prompt;

	private final ObjectMapper json = new ObjectMapper();

	public SkillGrader(SpecPromptCaller caller, String prompt) {
		this.caller = caller;
		this.prompt = prompt;
	}

	/**
	 * Grades an answer against the eval's expectations.
	 * @param task the scenario prompt the answer was produced for.
	 * @param answer the run's answer.
	 * @param expectations the verifiable statements to check.
	 * @return the grading, one entry per expectation, in order.
	 */
	public Grading grade(String task, String answer, List<String> expectations) {
		StringBuilder user = new StringBuilder();
		user.append("TASK:\n").append(task).append("\n\nANSWER:\n").append(answer).append("\n\nEXPECTATIONS:\n");
		int i = 1;
		for (String expectation : expectations) {
			user.append(i++).append(". ").append(expectation).append("\n");
		}
		JsonNode array = LlmJson.callAndParse(this.caller, this.prompt, user.toString(), 3, "the grading",
				(raw) -> this.json.readTree(raw).path("expectations"));

		List<GradedExpectation> graded = new ArrayList<>();
		for (int idx = 0; idx < expectations.size(); idx++) {
			String text = expectations.get(idx);
			boolean passed = false;
			String evidence = "not graded";
			if (array.isArray() && idx < array.size()) {
				JsonNode e = array.get(idx);
				passed = e.path("passed").asBoolean(false);
				evidence = e.path("evidence").asText("");
				String modelText = e.path("text").asText("");
				if (!modelText.isBlank()) {
					text = modelText;
				}
			}
			graded.add(new GradedExpectation(text, passed, evidence));
		}
		return new Grading(graded);
	}

	/**
	 * A grading result: one graded expectation per statement.
	 *
	 * @param expectations the graded expectations, in the eval's order.
	 */
	public record Grading(List<GradedExpectation> expectations) {

		public Grading {
			expectations = (expectations != null) ? List.copyOf(expectations) : List.of();
		}

		/**
		 * Counts the expectations that passed.
		 * @return the number of expectations that passed.
		 */
		public int passed() {
			return (int) this.expectations.stream().filter(GradedExpectation::passed).count();
		}

		/**
		 * Counts the expectations graded.
		 * @return the total number of expectations graded.
		 */
		public int total() {
			return this.expectations.size();
		}

	}

	/**
	 * A single graded expectation.
	 *
	 * @param text the expectation statement.
	 * @param passed whether the answer satisfies it.
	 * @param evidence a short quote or reason.
	 */
	public record GradedExpectation(String text, boolean passed, String evidence) {
	}

}
