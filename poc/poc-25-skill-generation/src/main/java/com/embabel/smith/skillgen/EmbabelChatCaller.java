/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.skillgen;

import com.embabel.agent.api.common.Ai;

/**
 * Arm 2 — the runtime transport: the same with/without-skill calls, but routed through
 * Embabel's {@link Ai} LLM abstraction (the product's model layer — roles,
 * {@code LlmOptions}, the OpenAI-compatible provider) instead of a hand-rolled HTTP
 * client. The system and user prompts are combined into a single instruction so the
 * content is identical to the raw arm; only the transport differs.
 *
 * @author Ivan Rodriguez
 */
public class EmbabelChatCaller implements ChatCaller {

	private final Ai ai;

	public EmbabelChatCaller(Ai ai) {
		this.ai = ai;
	}

	@Override
	public String call(String system, String user) {
		String instruction = (system == null || system.isBlank()) ? user : system + "\n\n---\n\n" + user;
		return this.ai.withDefaultLlm().generateText(instruction);
	}

}
