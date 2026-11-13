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

package com.embabel.smith.gencmp;

import java.util.List;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.spec.compile.AgentCompilationResult;
import com.embabel.agent.spec.compile.DefaultNaturalLanguageCompiler;
import com.embabel.agent.spec.model.StepSpecContext;
import com.embabel.common.ai.model.LlmOptions;
import com.embabel.smith.common.model.AgentSpec;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Approach B — Embabel's {@code embabel-agent-spec} NL compiler. The flat baseline is a single
 * {@code compileAgent} call. The multi-agent mode first asks the model to decompose the process
 * into coherent sub-agents (a plain {@link LlmCaller} call), then compiles each sub-agent with
 * the shared {@link DataDictionary} so intermediate types are shared across agents — the same
 * composition the runtime performs when it ingests several packs.
 *
 * @author Ivan Rodriguez
 */
public class EmbabelGenerator {

	private final Ai ai;

	private final DataDictionary dataDictionary;

	private final LlmCaller decomposer;

	private final String decompositionPrompt;

	private final CompilationResultToAgentSpec mapper = new CompilationResultToAgentSpec();

	private final ObjectMapper json = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
			false);

	public EmbabelGenerator(Ai ai, DataDictionary dataDictionary, LlmCaller decomposer, String decompositionPrompt) {
		this.ai = ai;
		this.dataDictionary = dataDictionary;
		this.decomposer = decomposer;
		this.decompositionPrompt = decompositionPrompt;
	}

	/**
	 * The flat baseline: one agent from one compileAgent call.
	 * @param name the agent name to compile under.
	 * @param blueprint the business-process description to compile.
	 * @return the single-agent process.
	 */
	public ProcessSpec generateFlat(String name, String blueprint) {
		AgentSpec agent = this.mapper.toAgentSpec(this.compile(name, blueprint), name);
		return ProcessSpec.single(name, agent);
	}

	/**
	 * The multi-agent mode: decompose into sub-agents, then compile each against the shared types.
	 * @param name the process name to fall back to when the model does not name it.
	 * @param blueprint the business-process description to decompose and compile.
	 * @return the multi-agent process.
	 */
	public ProcessSpec generateMulti(String name, String blueprint) {
		String raw = this.decomposer.call(this.decompositionPrompt,
				"Below is a business-process description. Decompose it into coherent agents.\n\n" + blueprint);
		DecompositionResponse decomposition = this.read(SingleShotGenerator.extractJson(raw),
				DecompositionResponse.class);
		List<AgentSpec> agents = decomposition.agents().stream()
			.map((sub) -> this.mapper.toAgentSpec(this.compile(sub.name(), sub.description()), sub.name()))
			.toList();
		String process = (decomposition.process() != null && !decomposition.process().isBlank())
				? decomposition.process() : name;
		return new ProcessSpec(process, agents);
	}

	private <T> T read(String json, Class<T> type) {
		try {
			return this.json.readValue(json, type);
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to parse the model answer as " + type.getSimpleName() + ": "
					+ ex.getMessage(), ex);
		}
	}

	private AgentCompilationResult compile(String agentName, String description) {
		DefaultNaturalLanguageCompiler compiler = new DefaultNaturalLanguageCompiler(this.ai, LlmOptions.withDefaultLlm());
		StepSpecContext context = new StepSpecContext(agentName, this.dataDictionary, List.of(), List.of(), List.of());
		return compiler.compileAgent(agentName, description, context);
	}

	/**
	 * The decomposition response: a process name plus the ordered sub-agent descriptions.
	 * @param process the process name.
	 * @param agents the ordered sub-agent descriptions.
	 */
	record DecompositionResponse(String process, List<SubAgent> agents) {
	}

	/**
	 * A single sub-agent to compile: a name and a self-contained description.
	 * @param name the sub-agent name.
	 * @param description the self-contained sub-agent description.
	 */
	record SubAgent(String name, String description) {
	}

}
