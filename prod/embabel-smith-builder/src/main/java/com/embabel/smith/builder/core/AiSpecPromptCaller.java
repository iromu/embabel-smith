/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.core;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.api.common.PromptRunner;
import com.embabel.agent.api.reference.LlmReference;
import com.embabel.common.ai.model.LlmOptions;

/**
 * The production {@link SpecPromptCaller}: the builder's single model layer, Embabel's
 * {@link Ai}, so both generation techniques share one endpoint configuration and one set
 * of roles. The system prompt is sent as one, so the model sees the same message shapes
 * its own conversation API defines. The answer is a whole agent spec, so the token budget
 * is generous and the temperature low.
 * <p>
 * It is also the production {@link ReferenceAwarePromptCaller}, which is what lets a
 * builder call offer a capability's tools — a {@code Skills} pack's scripts, say — beside
 * the prompt instead of only its text. The reference is attached to the same
 * {@link PromptRunner} the plain call builds, so a reference-carrying call differs from a
 * plain one by the reference and nothing else: same LLM pinning, same system prompt, same
 * options.
 *
 * @author Ivan Rodriguez
 */
public class AiSpecPromptCaller implements SpecPromptCaller, ReferenceAwarePromptCaller {

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
		return call(system, user, (String) null);
	}

	@Override
	public String call(String system, String user, String model) {
		return runner(system, model).generateText(user);
	}

	/**
	 * The same call with a capability attached to it.
	 * <p>
	 * {@code PromptRunner.withReference} is the framework's own seam, and it is two calls
	 * and nothing more (Embabel 1.5.4, {@code PromptRunner.kt:248-251}):
	 * {@code withToolObject(ToolObject(reference.tools(), reference.namingStrategy()))},
	 * then {@code withPromptContributor(reference)}. So the tools on offer are whatever
	 * {@code reference.tools()} answered when the call was assembled — and a
	 * {@code Skills} pack answers that with every skill's script tools whether or not the
	 * model has activated the skill, upstream's stated reason being that the tool list is
	 * not refreshed after {@code activate()} ({@code Skills.kt:198-201}). <em>When</em>
	 * the list is read is inferred from behaviour rather than read from source: a pack
	 * whose script folder had already been removed offered no script tool while the same
	 * skill still activated and still read its body back, so the listing happens per call
	 * and not once when the {@code Skills} was built. That timing is the part a future
	 * Embabel is free to change, so it is written here as an inference and not as a
	 * mechanism.
	 * @param system the system prompt, or empty for none
	 * @param user the user turn
	 * @param reference the capability whose tools the model is offered
	 * @return the model's answer
	 */
	@Override
	public String call(String system, String user, LlmReference reference) {
		return runner(system, null).withReference(reference).generateText(user);
	}

	private PromptRunner runner(String system, String model) {
		PromptRunner runner = this.ai.withLlm(pinnedTo(model, this.options));
		if (system != null && !system.isBlank()) {
			runner = runner.withSystemPrompt(system);
		}
		return runner;
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
