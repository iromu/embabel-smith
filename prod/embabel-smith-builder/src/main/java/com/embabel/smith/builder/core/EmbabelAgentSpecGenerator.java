/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
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
		return generate(description, null);
	}

	@Override
	public CompiledDraft generate(String description, String model) {
		String name = PackName.slug(description);
		AgentCompilationResult result = this.compiler.compile(name, description, this.dataDictionary, model);
		List<String> issues = result.getErrors().stream().map(CompilationError::getMessage).toList();
		return new CompiledDraft(name, this.mapper.toAgentSpec(result, name), issues);
	}

}
