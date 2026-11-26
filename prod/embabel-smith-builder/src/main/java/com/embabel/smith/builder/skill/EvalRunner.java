/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.skill;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.embabel.agent.skills.Skills;
import com.embabel.smith.builder.core.ReferenceAwarePromptCaller;
import com.embabel.smith.builder.core.SpecPromptCaller;

/**
 * Runs an {@link EvalSuite} the way the skill-creator does: for each eval, a with-skill
 * run (the generated {@code SKILL.md} embedded in the system prompt — simulating skill
 * activation) and a without-skill baseline (a plain system prompt), each graded against
 * the eval's expectations. Both arms go through the same {@link SpecPromptCaller}, so the
 * only difference between them is whether the skill is in context — which is what makes
 * the difference in pass rate attributable to the skill.
 * <p>
 * <b>With a {@link SkillScriptHost}, the with-skill arm can also run the skill's
 * scripts.</b> A host exists only when {@code smith.builder.skill-scripts.enabled}
 * installed the engine bean, so an ordinary build changes nothing here: one prompt
 * substitution per arm, no tool surface in either. With a host, the with-skill call
 * additionally carries the skill as an Embabel reference, which is where a
 * {@code <skill>_<script>} tool comes from; the baseline arm still goes through the plain
 * call and carries nothing. That is a different quantity from the prompt-only lift this
 * runner measured before a host could exist: with the switch on, the with arm holds a
 * capability the baseline arm does not, so the gap measures "a skill you can execute"
 * against "no skill" rather than "a skill as instructions" against "no skill". The switch
 * is the explicit decision for that, and the lift gate's thresholds were set against the
 * prompt-only number.
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

	/** Null unless the script-engine bean was installed; see {@link SkillScriptHost}. */
	private final SkillScriptHost scriptHost;

	/**
	 * The reference-carrying view of {@link #caller}, null exactly when it has no use.
	 */
	private final ReferenceAwarePromptCaller referencedCaller;

	public EvalRunner(SpecPromptCaller caller, SkillGrader grader, String withSkillPrompt, String baselinePrompt) {
		this(caller, grader, withSkillPrompt, baselinePrompt, null);
	}

	/**
	 * Builds the two arms.
	 * @param caller the model layer both arms and the judge go through.
	 * @param grader the judge.
	 * @param withSkillPrompt the with-skill system prompt, carrying the {@code {skill}}
	 * marker.
	 * @param baselinePrompt the no-skill system prompt.
	 * @param scriptHost the builder's script host, or null to run the arms as prompt
	 * substitution alone — which is what a build without the engine bean does.
	 */
	public EvalRunner(SpecPromptCaller caller, SkillGrader grader, String withSkillPrompt, String baselinePrompt,
			SkillScriptHost scriptHost) {
		this.caller = caller;
		this.grader = grader;
		this.withSkillPrompt = withSkillPrompt;
		this.baselinePrompt = baselinePrompt;
		this.scriptHost = scriptHost;
		this.referencedCaller = (scriptHost != null) ? carrying(caller) : null;
	}

	/**
	 * The caller seen as one that can carry a reference.
	 * <p>
	 * Failing here rather than degrading to the plain call is deliberate: a host that
	 * quietly stopped attaching its skill would leave the with-skill arm measuring
	 * exactly what it measured before scripts existed, and every test of the host would
	 * still pass, because a lambda fake answers either way.
	 * @param caller the model layer this runner was built with.
	 * @return the same object, seen through the reference-carrying call.
	 */
	private static ReferenceAwarePromptCaller carrying(SpecPromptCaller caller) {
		if (caller instanceof ReferenceAwarePromptCaller carrying) {
			return carrying;
		}
		throw new IllegalStateException("a script host needs a model layer that carries an LlmReference; "
				+ caller.getClass().getName() + " would answer the with-skill arm as though nothing were attached");
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
		// The reference is made once for the whole run: it is the skill's folder, and the
		// arms differ by whether it is attached, not by anything recomputed per eval.
		Skills scripts = (this.scriptHost != null) ? skillsFor(this.scriptHost, skill) : null;
		for (EvalSuite.Eval eval : evals.evals()) {
			String withAnswer = (scripts != null) ? this.referencedCaller.call(withSystem, eval.prompt(), scripts)
					: this.caller.call(withSystem, eval.prompt());
			runs.add(new EvalRun(eval, WITH_SKILL, withAnswer,
					this.grader.grade(eval.prompt(), withAnswer, eval.expectations())));

			String withoutAnswer = this.caller.call(this.baselinePrompt, eval.prompt());
			runs.add(new EvalRun(eval, WITHOUT_SKILL, withoutAnswer,
					this.grader.grade(eval.prompt(), withoutAnswer, eval.expectations())));
		}
		return runs;
	}

	/**
	 * The skill as a callable pack, for a build that installed the script engine.
	 * @param host this runner's script host.
	 * @param skill the skill under evaluation.
	 * @return the reference the with-skill arm carries.
	 */
	private static Skills skillsFor(SkillScriptHost host, Skill skill) {
		try {
			return host.skillsFor(skill);
		}
		catch (IOException couldNotWrite) {
			throw new IllegalStateException("unable to open " + skill.name() + " as a skill folder to run its scripts",
					couldNotWrite);
		}
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
