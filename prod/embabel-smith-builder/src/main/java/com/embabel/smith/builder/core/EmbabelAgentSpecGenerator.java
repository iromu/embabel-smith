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

import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.spec.compile.AgentCompilationResult;
import com.embabel.agent.spec.compile.CompilationError;

/**
 * The embabel generation technique: Embabel's NL compiler turns the description into a
 * draft, and {@link DraftToAgentSpec} maps it to the shared agent-spec format — including
 * the goal normalization that makes the goal output a returnable type. This is the
 * technique's only home for Embabel-specific types; everything downstream of generation
 * sees an {@link com.embabel.smith.common.model.AgentSpec}.
 *
 * @author Ivan Rodriguez
 */
public class EmbabelAgentSpecGenerator implements AgentSpecGenerator {

	private final AgentSpecCompiler compiler;

	private final DataDictionary dataDictionary;

	private final DraftToAgentSpec mapper = new DraftToAgentSpec();

	public EmbabelAgentSpecGenerator(AgentSpecCompiler compiler, DataDictionary dataDictionary) {
		this.compiler = compiler;
		this.dataDictionary = dataDictionary;
	}

	@Override
	public CompiledDraft generate(String description) {
		String name = PackName.slug(description);
		AgentCompilationResult result = this.compiler.compile(name, description, this.dataDictionary);
		List<String> issues = result.getErrors().stream().map(CompilationError::getMessage).toList();
		return new CompiledDraft(name, this.mapper.toAgentSpec(result, name), issues);
	}

}
