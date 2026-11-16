/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.core;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.api.common.PromptRunner;
import com.embabel.common.ai.model.LlmOptions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Offline tests of the builder's model caller: the system prompt arrives as its own
 * message rather than glued onto the user content, and a caller with nothing to say as
 * system prompt sends none. The model layer is mocked, so nothing here touches an
 * endpoint.
 */
class AiSpecPromptCallerTest {

	@Test
	void sendsTheSystemPromptSeparatelyFromTheUserContent() {
		PromptRunner runner = runnerReturning("answer");
		AiSpecPromptCaller caller = new AiSpecPromptCaller(ai(runner), LlmOptions.withDefaults());

		assertThat(caller.call("SYSTEM-PROMPT", "USER-CONTENT")).isEqualTo("answer");

		verify(runner).withSystemPrompt("SYSTEM-PROMPT");
		ArgumentCaptor<String> instruction = ArgumentCaptor.forClass(String.class);
		verify(runner).generateText(instruction.capture());
		assertThat(instruction.getValue()).isEqualTo("USER-CONTENT");
	}

	@Test
	void aBlankSystemPromptSendsNoSystemPrompt() {
		PromptRunner runner = runnerReturning("answer");
		AiSpecPromptCaller caller = new AiSpecPromptCaller(ai(runner), LlmOptions.withDefaults());

		caller.call("  ", "USER-CONTENT");

		verify(runner, never()).withSystemPrompt(anyString());
		verify(runner).generateText("USER-CONTENT");
	}

	private static Ai ai(PromptRunner runner) {
		Ai ai = mock(Ai.class);
		when(ai.withLlm(any(LlmOptions.class))).thenReturn(runner);
		return ai;
	}

	private static PromptRunner runnerReturning(String answer) {
		PromptRunner runner = mock(PromptRunner.class);
		when(runner.withSystemPrompt(anyString())).thenReturn(runner);
		when(runner.generateText(anyString())).thenReturn(answer);
		return runner;
	}

}
