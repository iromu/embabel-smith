/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.eval;

import java.util.List;

import com.embabel.agent.api.common.Ai;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.validation.AgentSpecValidator;

/**
 * The eval → rewrite loop (task 6.2, stretch). Given a failing eval task, the spec's
 * prompt is revised and the revised draft is re-validated.
 *
 * <p>
 * Two halves: {@link #rewritePrompt} is endpoint-gated (asks the LLM to revise the
 * prompt); {@link #applyRevisedPrompt} is pure Java (splices the revised prompt into the
 * draft and re-runs the group-1 validation), so the re-validation half is verifiable
 * offline.
 *
 * @author Ivan Rodriguez
 */
public class PromptRewriteLoop {

	private final AgentSpecValidator validator = new AgentSpecValidator();

	/**
	 * Endpoint-gated: ask the LLM to rewrite the prompt so it handles the failing case
	 * better.
	 * @param ai the live LLM client
	 * @param currentPrompt the prompt to revise
	 * @param failingTask the eval task that failed
	 * @return the revised prompt text
	 */
	public String rewritePrompt(Ai ai, String currentPrompt, SpecEvalTask failingTask) {
		if (ai == null) {
			throw new IllegalStateException("rewritePrompt requires a live LLM (an Ai bean)");
		}
		String instruction = """
				Rewrite the following spec-action prompt so that it handles the failing case better.

				Failing input: %s
				Acceptance criteria: %s

				Current prompt:
				%s

				Return ONLY the revised prompt text.
				""".formatted(failingTask.input(), failingTask.acceptanceCriteria(), currentPrompt);
		return ai.withDefaultLlm().generateText(instruction);
	}

	/**
	 * Pure: splice a revised prompt into the named step and re-validate the revised
	 * draft. Nothing is written; the caller decides what to do with the result.
	 * @param pack the draft spec to revise
	 * @param stepName the name of the step whose prompt is replaced
	 * @param revisedPrompt the revised prompt text
	 * @return the revised prompt, the revised draft, and its re-validation result
	 */
	public RewriteResult applyRevisedPrompt(AgentSpec pack, String stepName, String revisedPrompt) {
		List<ActionStepSpec> newSteps = pack.steps()
			.stream()
			.map((s) -> s.name().equals(stepName) ? withPrompt(s, revisedPrompt) : s)
			.toList();
		AgentSpec revised = new AgentSpec(pack.manifest(), pack.goal(), newSteps, pack.types());
		List<String> issues = this.validator.validate(revised)
			.issues()
			.stream()
			.map((issue) -> issue.code() + " — " + issue.message())
			.toList();
		return new RewriteResult(revisedPrompt, revised, issues.isEmpty(), issues);
	}

	private ActionStepSpec withPrompt(ActionStepSpec s, String prompt) {
		return new ActionStepSpec(s.stepType(), s.name(), s.description(), s.inputTypeNames(), s.outputTypeName(),
				prompt, s.pre(), s.post(), s.cost(), s.value(), s.canRerun(), s.nullable(), s.toolGroups());
	}

	/**
	 * The result of a rewrite: the revised prompt, the revised draft, and whether it
	 * re-validates.
	 *
	 * @param revisedPrompt the revised prompt text
	 * @param revisedPack the revised draft spec
	 * @param valid whether the revised draft re-validates cleanly
	 * @param issues the validation issues on the revised draft
	 */
	public record RewriteResult(String revisedPrompt, AgentSpec revisedPack, boolean valid, List<String> issues) {
		public RewriteResult {
			issues = (issues != null) ? List.copyOf(issues) : List.of();
		}
	}

}
