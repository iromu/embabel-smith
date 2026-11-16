/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.core;

/**
 * The model seam behind {@link SingleShotAgentSpecGenerator}: a system prompt plus a user
 * message in, the raw completion out. The production implementation
 * ({@link AiSpecPromptCaller}) goes through Embabel's {@code Ai} layer; offline tests
 * inject a canned answer so parsing and normalisation are verifiable without a model.
 *
 * @author Ivan Rodriguez
 */
@FunctionalInterface
public interface SpecPromptCaller {

	String call(String system, String user);

	/**
	 * As {@link #call(String, String)}, asking for one particular LLM. The default
	 * ignores the request, so a caller that only answers for the configured model — a
	 * canned offline fake — keeps working; the production implementation honours it.
	 * @param system the system prompt, or empty for none
	 * @param user the user message
	 * @param model the LLM to run, or empty for the configured default
	 * @return the raw completion
	 */
	default String call(String system, String user, String model) {
		return call(system, user);
	}

}
