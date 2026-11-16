/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.eval;

import com.embabel.agent.api.common.Ai;
import com.embabel.common.ai.model.LlmOptions;
import com.embabel.smith.builder.core.AiSpecPromptCaller;

/**
 * The production {@link PromptRewriter}: asks the model, through Embabel's {@link Ai} —
 * the same model layer the builder generates with — to revise an action prompt so it
 * satisfies the failing eval criteria (POC 9, the SkillAxe pattern). The model comes from
 * platform configuration, so a deployment can move rewriting to another model or role
 * without a code change. Endpoint-gated — the call hits the model.
 *
 * @author Ivan Rodriguez
 */
public class AiPromptRewriter implements PromptRewriter {

	private static final String SYSTEM_PROMPT = "You are an expert at writing precise LLM action prompts.";

	private final Ai ai;

	private final LlmOptions options;

	public AiPromptRewriter(Ai ai) {
		this(ai, LlmOptions.withDefaults());
	}

	public AiPromptRewriter(Ai ai, LlmOptions options) {
		this.ai = ai;
		this.options = options;
	}

	@Override
	public String rewrite(String currentPrompt, String failureFeedback) {
		return rewrite(currentPrompt, failureFeedback, null);
	}

	@Override
	public String rewrite(String currentPrompt, String failureFeedback, String model) {
		String instruction = """
				You revise the prompt of a single spec action so it reliably produces output that
				satisfies the failing acceptance criteria below. Keep it a single action prompt;
				preserve the {{...}} template variables; return ONLY the revised prompt text, no commentary.

				Failing eval tasks:
				%s

				Current prompt:
				%s
				""".formatted(failureFeedback, currentPrompt);
		String content = this.ai.withLlm(AiSpecPromptCaller.pinnedTo(model, this.options))
			.withSystemPrompt(SYSTEM_PROMPT)
			.generateText(instruction);
		return (content == null || content.isBlank()) ? currentPrompt : content.trim();
	}

}
