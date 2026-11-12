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

package com.embabel.smith.nlcompile;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.spec.compile.AgentCompilationResult;
import com.embabel.agent.spec.compile.DefaultNaturalLanguageCompiler;
import com.embabel.agent.spec.model.StepSpecContext;
import com.embabel.common.ai.model.LlmOptions;

/**
 * De-risks the NL-&gt;draft path (POC 2): turns a plain-language description into an
 * {@link AgentCompilationResult} (actions + exported goal + intermediate types + issues
 * to review) via Embabel's {@link DefaultNaturalLanguageCompiler}.
 *
 * <p>
 * The result is a <em>draft</em>, not a finished spec: when the description is ambiguous
 * the compiler returns a draft together with explicit issues ({@code getErrors()}) to
 * review, rather than silently guessing.
 *
 * @author Ivan Rodriguez
 */
public class NlCompileRunner {

	private final DefaultNaturalLanguageCompiler compiler;

	private final StepSpecContextFactory contextFactory;

	private final DataDictionary dataDictionary;

	public NlCompileRunner(Ai ai, DataDictionary dataDictionary, LlmOptions llmOptions) {
		this.compiler = new DefaultNaturalLanguageCompiler(ai, llmOptions);
		this.contextFactory = new StepSpecContextFactory();
		this.dataDictionary = dataDictionary;
	}

	public AgentCompilationResult compile(String agentName, String description) {
		StepSpecContext context = this.contextFactory.forAgent(agentName, this.dataDictionary);
		return this.compiler.compileAgent(agentName, description, context);
	}

}
