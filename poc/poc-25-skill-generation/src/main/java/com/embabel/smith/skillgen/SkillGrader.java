/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.embabel.smith.skillgen;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The LLM-as-judge grader (the skill-creator's grader step). Given a run's answer and the
 * eval's verifiable expectations, it decides pass/fail for each, with a short piece of
 * evidence. The result is the skill-creator {@code grading.json} shape: an
 * {@code expectations} array of {@code {text, passed, evidence}}.
 *
 * @author Ivan Rodriguez
 */
public class SkillGrader {

	private final ChatCaller caller;

	private final String prompt;

	private final ObjectMapper json = new ObjectMapper();

	public SkillGrader(ChatCaller caller, String prompt) {
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
				(json) -> this.json.readTree(json).path("expectations"));

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
	 * @param text the expectation statement.
	 * @param passed whether the answer satisfies it.
	 * @param evidence a short quote or reason.
	 */
	public record GradedExpectation(String text, boolean passed, String evidence) {
	}

}
