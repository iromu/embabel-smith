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

/**
 * Runs an {@link EvalSuite} the way the skill-creator does: for each eval, a with-skill run
 * (the generated {@code SKILL.md} embedded in the system prompt — simulating skill activation)
 * and a without-skill baseline (a plain system prompt), each graded against the eval's
 * expectations. The skill content is identical for both arms; only the {@link ChatCaller}
 * transport differs.
 *
 * @author Ivan Rodriguez
 */
public class EvalRunner {

	private final ChatCaller caller;

	private final SkillGrader grader;

	private final String withSkillPrompt;

	private final String baselinePrompt;

	public EvalRunner(ChatCaller caller, SkillGrader grader, String withSkillPrompt, String baselinePrompt) {
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
			runs.add(new EvalRun(eval, "with_skill", withAnswer,
					this.grader.grade(eval.prompt(), withAnswer, eval.expectations())));

			String withoutAnswer = this.caller.call(this.baselinePrompt, eval.prompt());
			runs.add(new EvalRun(eval, "without_skill", withoutAnswer,
					this.grader.grade(eval.prompt(), withoutAnswer, eval.expectations())));
		}
		return runs;
	}

	/**
	 * A single eval run under one configuration.
	 * @param eval the eval that was run.
	 * @param config {@code with_skill} or {@code without_skill}.
	 * @param answer the run's answer.
	 * @param grading the grading of the answer against the eval's expectations.
	 */
	public record EvalRun(EvalSuite.Eval eval, String config, String answer, SkillGrader.Grading grading) {
	}

}
