/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.core;

import java.util.List;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.api.common.PromptRunner;
import com.embabel.agent.api.reference.LlmReference;
import com.embabel.agent.skills.Skills;
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

		verify(runner, never()).withReference(any());
		verify(runner).withSystemPrompt("SYSTEM-PROMPT");
		ArgumentCaptor<String> instruction = ArgumentCaptor.forClass(String.class);
		verify(runner).generateText(instruction.capture());
		assertThat(instruction.getValue()).isEqualTo("USER-CONTENT");
	}

	/**
	 * The reference-carrying call, which is how the builder's with-skill eval arm offers
	 * a skill's scripts as tools. It is asserted on the production caller rather than on
	 * a fake because of how this seam is doubled elsewhere: a caller that forwards
	 * {@code call(system, user, reference)} to {@code call(system, user)} answers every
	 * fake exactly as a plain call does, so only the real implementation can show the
	 * reference reached the request.
	 */
	@Test
	void attachesAReferenceToTheSameRunnerThatCarriesThePrompt() {
		PromptRunner runner = runnerReturning("answer");
		when(runner.withReference(any(LlmReference.class))).thenReturn(runner);
		AiSpecPromptCaller caller = new AiSpecPromptCaller(ai(runner), LlmOptions.withDefaults());
		Skills skills = new Skills("cold-store-goods-receipt", "a skill to offer", List.of());

		assertThat(caller.call("SYSTEM-PROMPT", "USER-CONTENT", skills)).isEqualTo("answer");

		verify(runner).withReference(skills);
		verify(runner).withSystemPrompt("SYSTEM-PROMPT");
		verify(runner).generateText("USER-CONTENT");
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
