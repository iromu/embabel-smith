/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.skillgen;

import java.util.List;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

/**
 * An eval suite in the Anthropic skill-creator {@code evals/evals.json} format: a skill
 * name plus a list of scenario evals, each with a prompt, an expected output, optional
 * input files, and a list of verifiable expectations. The same suite is run with the
 * skill loaded and without it (the baseline); each run is graded against the
 * expectations.
 *
 * @param skillName the name the suite is written for (matches the skill frontmatter).
 * @param evals the scenario evals.
 * @author Ivan Rodriguez
 */
public record EvalSuite(String skillName, List<Eval> evals) {

	private static final ObjectMapper JSON = new ObjectMapper()
		.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
		.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

	public EvalSuite {
		evals = (evals != null) ? List.copyOf(evals) : List.of();
	}

	/**
	 * Parses an {@code evals.json} document.
	 * @param json the raw evals.json content.
	 * @return the parsed suite.
	 */
	public static EvalSuite parse(String json) {
		try {
			return JSON.readValue(json, EvalSuite.class);
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to parse evals.json: " + ex.getMessage(), ex);
		}
	}

	/**
	 * A single scenario eval.
	 *
	 * @param id the unique integer identifier.
	 * @param prompt the scenario task to execute.
	 * @param expectedOutput a human-readable description of a correct result.
	 * @param files optional input file paths (relative to the skill root).
	 * @param expectations verifiable statements a correct answer must satisfy.
	 */
	public record Eval(int id, String prompt, String expectedOutput, List<String> files, List<String> expectations) {

		public Eval {
			files = (files != null) ? List.copyOf(files) : List.of();
			expectations = (expectations != null) ? List.copyOf(expectations) : List.of();
		}

	}

}
