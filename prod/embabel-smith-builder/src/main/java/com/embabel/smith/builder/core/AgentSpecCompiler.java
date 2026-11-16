/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.core;

import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.spec.compile.AgentCompilationResult;

/**
 * The seam for the build's compile step: turns a natural-language description into an
 * {@link AgentCompilationResult}. The production implementation
 * ({@link DefaultAgentSpecCompiler}) calls the model; offline tests inject a fake that
 * returns a hand-built result, so the whole pipeline is verifiable without a live model.
 *
 * @author Ivan Rodriguez
 */
public interface AgentSpecCompiler {

	AgentCompilationResult compile(String agentName, String description, DataDictionary dataDictionary);

	/**
	 * As {@link #compile(String, String, DataDictionary)}, compiling with one particular
	 * LLM. The default ignores the request, so an offline fake that answers for the
	 * configured model keeps working; {@link DefaultAgentSpecCompiler} honours it.
	 * @param agentName the name to compile the agent under
	 * @param description the natural-language description to compile
	 * @param dataDictionary the types the compiled spec may bind
	 * @param model the LLM to compile with, or empty for the configured default
	 * @return the compilation result
	 */
	default AgentCompilationResult compile(String agentName, String description, DataDictionary dataDictionary,
			String model) {
		return compile(agentName, description, dataDictionary);
	}

}
