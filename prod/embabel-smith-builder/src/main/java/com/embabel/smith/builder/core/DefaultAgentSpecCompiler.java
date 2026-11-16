/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.core;

import java.util.List;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.spec.compile.AgentCompilationResult;
import com.embabel.agent.spec.compile.DefaultNaturalLanguageCompiler;
import com.embabel.agent.spec.model.StepSpecContext;
import com.embabel.common.ai.model.LlmOptions;

/**
 * The production compile step: wraps Embabel's {@link DefaultNaturalLanguageCompiler}.
 * Endpoint-gated — this is the only step in the build that calls the model.
 *
 * @author Ivan Rodriguez
 */
public class DefaultAgentSpecCompiler implements AgentSpecCompiler {

	private final Ai ai;

	private final LlmOptions options;

	public DefaultAgentSpecCompiler(Ai ai) {
		this(ai, LlmOptions.withDefaults());
	}

	public DefaultAgentSpecCompiler(Ai ai, LlmOptions options) {
		this.ai = ai;
		this.options = options;
	}

	@Override
	public AgentCompilationResult compile(String agentName, String description, DataDictionary dataDictionary) {
		return compile(agentName, description, dataDictionary, null);
	}

	@Override
	public AgentCompilationResult compile(String agentName, String description, DataDictionary dataDictionary,
			String model) {
		DefaultNaturalLanguageCompiler compiler = new DefaultNaturalLanguageCompiler(this.ai,
				AiSpecPromptCaller.pinnedTo(model, this.options));
		StepSpecContext context = new StepSpecContext(agentName, dataDictionary, List.of(), List.of(), List.of());
		return compiler.compileAgent(agentName, description, context);
	}

}
