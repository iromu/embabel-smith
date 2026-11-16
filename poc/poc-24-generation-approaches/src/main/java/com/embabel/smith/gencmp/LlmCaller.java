/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.gencmp;

/**
 * The model-call seam for the single-shot approach: a system prompt plus a user prompt
 * in, the raw completion text out. The production implementation is a raw
 * OpenAI-compatible chat-completions call (endpoint-gated); tests use a stub.
 *
 * @author Ivan Rodriguez
 */
@FunctionalInterface
public interface LlmCaller {

	String call(String system, String user);

}
