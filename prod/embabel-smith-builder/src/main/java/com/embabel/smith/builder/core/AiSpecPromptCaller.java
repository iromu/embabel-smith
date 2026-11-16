/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.core;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.api.common.PromptRunner;
import com.embabel.common.ai.model.LlmOptions;

/**
 * The production {@link SpecPromptCaller}: the builder's single model layer, Embabel's
 * {@link Ai}, so both generation techniques share one endpoint configuration and one set
 * of roles. The system prompt is sent as one, so the model sees the same message shapes
 * its own conversation API defines. The answer is a whole agent spec, so the token budget
 * is generous and the temperature low.
 *
 * @author Ivan Rodriguez
 */
public class AiSpecPromptCaller implements SpecPromptCaller {

	/** The generation answer is a full agent spec, so the budget must not truncate it. */
	private static final int MAX_TOKENS = 12000;

	private final Ai ai;

	private final LlmOptions options;

	public AiSpecPromptCaller(Ai ai) {
		this(ai, LlmOptions.withDefaults().withTemperature(0.2).withMaxTokens(MAX_TOKENS));
	}

	public AiSpecPromptCaller(Ai ai, LlmOptions options) {
		this.ai = ai;
		this.options = options;
	}

	@Override
	public String call(String system, String user) {
		return call(system, user, null);
	}

	@Override
	public String call(String system, String user, String model) {
		PromptRunner runner = this.ai.withLlm(pinnedTo(model, this.options));
		if (system != null && !system.isBlank()) {
			runner = runner.withSystemPrompt(system);
		}
		return runner.generateText(user);
	}

	/**
	 * The given options with only the model replaced, for a caller that names the LLM it
	 * wants this call to use.
	 *
	 * <p>
	 * An {@code LlmOptions} selects by its criteria when it has one and only falls back
	 * to the model name without it, so the criteria has to be cleared for the name to
	 * mean anything. The copy is what gets changed: the options a caller passed in are
	 * shared across calls, usually as a bean.
	 * @param model the LLM to select, or empty to keep the given options as they are
	 * @param base the options to keep every other setting from
	 * @return options naming {@code model} and nothing else changed
	 */
	public static LlmOptions pinnedTo(String model, LlmOptions base) {
		if (model == null || model.isBlank()) {
			return base;
		}
		LlmOptions pinned = LlmOptions.withDefaults().withDefaultsFrom(base);
		pinned.setModelSelectionCriteria(null);
		pinned.setModel(model);
		return pinned;
	}

}
