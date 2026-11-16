/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.skill;

import java.util.ArrayList;
import java.util.List;

import com.embabel.smith.builder.core.SpecPromptCaller;

/**
 * Runs an {@link EvalSuite} the way the skill-creator does: for each eval, a with-skill
 * run (the generated {@code SKILL.md} embedded in the system prompt — simulating skill
 * activation) and a without-skill baseline (a plain system prompt), each graded against
 * the eval's expectations. Both arms go through the same {@link SpecPromptCaller}, so the
 * only difference between them is whether the skill is in context — which is what makes
 * the difference in pass rate attributable to the skill.
 *
 * @author Ivan Rodriguez
 */
public class EvalRunner {

	/** The configuration marker for a run with the skill loaded. */
	public static final String WITH_SKILL = "with_skill";

	/** The configuration marker for the no-skill baseline run. */
	public static final String WITHOUT_SKILL = "without_skill";

	private final SpecPromptCaller caller;

	private final SkillGrader grader;

	private final String withSkillPrompt;

	private final String baselinePrompt;

	public EvalRunner(SpecPromptCaller caller, SkillGrader grader, String withSkillPrompt, String baselinePrompt) {
		this.caller = caller;
		this.grader = grader;
		this.withSkillPrompt = withSkillPrompt;
		this.baselinePrompt = baselinePrompt;
	}

	/**
	 * Runs every eval, with and without the skill.
	 * @param skill the generated skill to load for the with-skill runs.
	 * @param evals the suite to run.
	 * @return the runs, two per eval (with-skill first, then the baseline).
	 */
	public List<EvalRun> run(Skill skill, EvalSuite evals) {
		String withSystem = this.withSkillPrompt.replace("{skill}", skill.toSkillMd());
		List<EvalRun> runs = new ArrayList<>();
		for (EvalSuite.Eval eval : evals.evals()) {
			String withAnswer = this.caller.call(withSystem, eval.prompt());
			runs.add(new EvalRun(eval, WITH_SKILL, withAnswer,
					this.grader.grade(eval.prompt(), withAnswer, eval.expectations())));

			String withoutAnswer = this.caller.call(this.baselinePrompt, eval.prompt());
			runs.add(new EvalRun(eval, WITHOUT_SKILL, withoutAnswer,
					this.grader.grade(eval.prompt(), withoutAnswer, eval.expectations())));
		}
		return runs;
	}

	/**
	 * A single eval run under one configuration.
	 *
	 * @param eval the eval that was run.
	 * @param config {@link #WITH_SKILL} or {@link #WITHOUT_SKILL}.
	 * @param answer the run's answer.
	 * @param grading the grading of the answer against the eval's expectations.
	 */
	public record EvalRun(EvalSuite.Eval eval, String config, String answer, SkillGrader.Grading grading) {
	}

}
