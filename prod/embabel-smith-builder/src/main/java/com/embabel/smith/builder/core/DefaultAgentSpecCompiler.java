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
		DefaultNaturalLanguageCompiler compiler = new DefaultNaturalLanguageCompiler(this.ai, this.options);
		StepSpecContext context = new StepSpecContext(agentName, dataDictionary, List.of(), List.of(), List.of());
		return compiler.compileAgent(agentName, description, context);
	}

}
