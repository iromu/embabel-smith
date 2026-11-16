/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.skillgen;

/**
 * The model-call seam for POC 25: a system prompt plus a user prompt in, the raw
 * completion text out. Two implementations run side by side — a raw OpenAI-compatible
 * call (the lightweight arm) and the Embabel {@code Ai} runtime (the runtime arm) — so
 * the same with/ without-skill comparison is measured over both transports.
 *
 * @author Ivan Rodriguez
 */
@FunctionalInterface
public interface ChatCaller {

	String call(String system, String user);

	/**
	 * A call whose completion is expected to be a single JSON object. The raw transport
	 * requests constrained JSON decoding so the answer is syntactically valid — which is
	 * what the decomposer, skill generator, eval-drafter and grader parse; other
	 * transports fall back to {@link #call}. Every JSON-parsing step reaches this through
	 * {@link LlmJson#callAndParse}.
	 * @param system the system prompt.
	 * @param user the user prompt.
	 * @return the raw completion text, expected to be a JSON object.
	 */
	default String callJson(String system, String user) {
		return call(system, user);
	}

}
