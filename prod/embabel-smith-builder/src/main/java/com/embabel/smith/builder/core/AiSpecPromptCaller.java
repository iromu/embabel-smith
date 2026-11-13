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

package com.embabel.smith.builder.core;

import com.embabel.agent.api.common.Ai;
import com.embabel.common.ai.model.LlmOptions;

/**
 * The production {@link SpecPromptCaller}: the builder's single model layer, Embabel's
 * {@link Ai}, so both generation techniques share one endpoint configuration and one set
 * of roles. The system and user prompts are concatenated into one instruction, so the
 * content sent matches the raw-prompt arm that POC 24 measured. The answer is a whole
 * agent spec, so the token budget is generous and the temperature low.
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
		String instruction = (system == null || system.isBlank()) ? user : system + "\n\n---\n\n" + user;
		return this.ai.withLlm(this.options).generateText(instruction);
	}

}
