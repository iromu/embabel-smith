/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.eval;

/**
 * Rewrites an action step's prompt in response to eval failures (extracted from POC 9's
 * refiner). The production implementation asks the model for a revised prompt; offline
 * tests inject a fake.
 *
 * @author Ivan Rodriguez
 */
@FunctionalInterface
public interface PromptRewriter {

	String rewrite(String currentPrompt, String failureFeedback);

	/**
	 * As {@link #rewrite(String, String)}, asking for one particular LLM. The default
	 * ignores the request, so a fake that only answers for the configured model keeps
	 * working; the production implementation honours it.
	 * @param currentPrompt the prompt to revise
	 * @param failureFeedback what the failing eval tasks said
	 * @param model the LLM to run, or empty for the configured default
	 * @return the revised prompt
	 */
	default String rewrite(String currentPrompt, String failureFeedback, String model) {
		return rewrite(currentPrompt, failureFeedback);
	}

}
