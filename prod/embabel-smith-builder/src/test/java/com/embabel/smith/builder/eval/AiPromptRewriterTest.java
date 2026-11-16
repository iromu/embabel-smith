/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.eval;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.api.common.PromptRunner;
import com.embabel.common.ai.model.LlmOptions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Offline tests of the model-layer rewriter: the eval feedback and the prompt being
 * revised reach the model, the persona arrives as a system prompt rather than as part of
 * the instruction, and a blank completion leaves the prompt unchanged rather than
 * blanking it. The model layer is mocked, so nothing here touches an endpoint.
 */
class AiPromptRewriterTest {

	@Test
	void sendsTheFeedbackAndPromptAndReturnsTheRevision() {
		PromptRunner runner = runnerReturning("  revised prompt  ");
		AiPromptRewriter rewriter = new AiPromptRewriter(ai(runner));

		assertThat(rewriter.rewrite("ORIGINAL-PROMPT", "step failed: no output")).isEqualTo("revised prompt");

		ArgumentCaptor<String> instruction = ArgumentCaptor.forClass(String.class);
		verify(runner).generateText(instruction.capture());
		assertThat(instruction.getValue()).contains("ORIGINAL-PROMPT").contains("step failed: no output");
	}

	@Test
	void sendsThePersonaAsASystemPrompt() {
		PromptRunner runner = runnerReturning("revised");
		AiPromptRewriter rewriter = new AiPromptRewriter(ai(runner));

		rewriter.rewrite("ORIGINAL-PROMPT", "feedback");

		ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
		verify(runner).withSystemPrompt(system.capture());
		assertThat(system.getValue()).contains("expert");
		ArgumentCaptor<String> instruction = ArgumentCaptor.forClass(String.class);
		verify(runner).generateText(instruction.capture());
		assertThat(instruction.getValue()).doesNotContain("expert");
	}

	@Test
	void aBlankCompletionLeavesThePromptUnchanged() {
		PromptRunner runner = runnerReturning("   ");

		assertThat(new AiPromptRewriter(ai(runner)).rewrite("ORIGINAL-PROMPT", "feedback"))
			.isEqualTo("ORIGINAL-PROMPT");
	}

	private static Ai ai(PromptRunner runner) {
		Ai ai = mock(Ai.class);
		when(ai.withLlm(any(LlmOptions.class))).thenReturn(runner);
		return ai;
	}

	private static PromptRunner runnerReturning(String completion) {
		PromptRunner runner = mock(PromptRunner.class);
		when(runner.withSystemPrompt(anyString())).thenReturn(runner);
		when(runner.generateText(anyString())).thenReturn(completion);
		return runner;
	}

}
