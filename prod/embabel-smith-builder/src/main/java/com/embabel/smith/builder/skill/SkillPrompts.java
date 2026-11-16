/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.skill;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The bundled prompts of the skill build: the skill generator, the eval drafter, the
 * LLM-as-judge grader, and the two eval arms (with-skill and the no-skill baseline). They
 * live on the builder classpath so a build has no external prompt dependency, and each is
 * overridable per service for experimentation.
 *
 * @author Ivan Rodriguez
 */
public final class SkillPrompts {

	private static final String GENERATE_SKILL = "/prompts/generate_skill.jinja";

	private static final String GENERATE_EVALS = "/prompts/generate_evals.jinja";

	private static final String GRADE_EVAL = "/prompts/grade_eval.jinja";

	private static final String AGENT_WITH_SKILL = "/prompts/agent_with_skill.jinja";

	private static final String AGENT_BASELINE = "/prompts/agent_baseline.jinja";

	private SkillPrompts() {
	}

	/**
	 * The system prompt that asks for the skill's {@code description} and {@code body}.
	 * @return the prompt template text.
	 */
	public static String generateSkill() {
		return load(GENERATE_SKILL);
	}

	/**
	 * The system prompt that drafts the {@code evals.json} suite.
	 * @return the prompt template text.
	 */
	public static String generateEvals() {
		return load(GENERATE_EVALS);
	}

	/**
	 * The system prompt of the LLM-as-judge grader.
	 * @return the prompt template text.
	 */
	public static String gradeEval() {
		return load(GRADE_EVAL);
	}

	/**
	 * The system prompt of the with-skill arm; its {@code {skill}} placeholder is
	 * replaced with the rendered {@code SKILL.md}.
	 * @return the prompt template text.
	 */
	public static String agentWithSkill() {
		return load(AGENT_WITH_SKILL);
	}

	/**
	 * The system prompt of the no-skill baseline arm.
	 * @return the prompt template text.
	 */
	public static String agentBaseline() {
		return load(AGENT_BASELINE);
	}

	private static String load(String resource) {
		try (InputStream in = SkillPrompts.class.getResourceAsStream(resource)) {
			if (in == null) {
				throw new IllegalStateException("prompt resource not found: " + resource);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to load prompt " + resource + ": " + ex.getMessage(), ex);
		}
	}

}
