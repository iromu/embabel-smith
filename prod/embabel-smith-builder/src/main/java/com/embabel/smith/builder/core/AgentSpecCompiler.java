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

}
