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

package com.embabel.smith.studio.generate;

import com.embabel.agent.core.DataDictionary;
import com.embabel.smith.builder.core.AgentSpecCompiler;
import com.embabel.smith.builder.core.DraftToAgentSpec;
import com.embabel.smith.builder.core.PackName;
import com.embabel.smith.common.model.AgentSpec;

/**
 * The embabel NL-compiler generation technique: compiles a business-process description into a
 * single agent spec through Embabel's NL compiler (via the builder's {@link AgentSpecCompiler}
 * seam) and maps the result to the shared agent-spec format with the builder's
 * {@link DraftToAgentSpec}. This is the alternative to the studio's single-shot
 * {@link ProcessFlowGenerator}; the Author-a-Process workspace lets the user pick either
 * technique.
 *
 * @author Ivan Rodriguez
 */
public class NlCompilerFlowGenerator {

	private final AgentSpecCompiler compiler;

	private final DataDictionary dataDictionary;

	private final DraftToAgentSpec mapper = new DraftToAgentSpec();

	public NlCompilerFlowGenerator(AgentSpecCompiler compiler, DataDictionary dataDictionary) {
		this.compiler = compiler;
		this.dataDictionary = dataDictionary;
	}

	/**
	 * Compiles the blueprint into an agent spec. The pack name is derived from the blueprint
	 * (the same rule the builder uses), and the goal is force-exported by the mapper.
	 * @param blueprint the business-process description to compile.
	 * @return the generated agent spec.
	 */
	public AgentSpec generate(String blueprint) {
		String name = PackName.slug(blueprint);
		return this.mapper.toAgentSpec(this.compiler.compile(name, blueprint, this.dataDictionary), name);
	}

}
