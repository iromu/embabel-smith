/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.embabel.smith.skillgen;

import com.embabel.agent.api.common.Ai;

/**
 * Arm 2 — the runtime transport: the same with/without-skill calls, but routed through
 * Embabel's {@link Ai} LLM abstraction (the product's model layer — roles, {@code LlmOptions},
 * the OpenAI-compatible provider) instead of a hand-rolled HTTP client. The system and user
 * prompts are combined into a single instruction so the content is identical to the raw arm;
 * only the transport differs.
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
