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

import java.util.Map;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.core.DataDictionary;

/**
 * The production wiring of the generation techniques: both generators share the
 * builder's single {@link Ai} model layer, so a build (and any host that reuses it, such
 * as the studio) selects a technique without changing its endpoint configuration.
 *
 * @author Ivan Rodriguez
 */
public final class AgentSpecGenerators {

	private AgentSpecGenerators() {
	}

	/**
	 * The generator of every technique, ready for a {@link BuilderAgent}.
	 * @param ai the Embabel AI service both techniques call through.
	 * @param dataDictionary the shared type dictionary the NL compiler compiles against.
	 * @return the generators keyed by technique.
	 */
	public static Map<GenerationTechnique, AgentSpecGenerator> production(Ai ai, DataDictionary dataDictionary) {
		return Map.of(GenerationTechnique.SINGLE_SHOT, new SingleShotAgentSpecGenerator(ai), GenerationTechnique.EMBABEL,
				new EmbabelAgentSpecGenerator(new DefaultAgentSpecCompiler(ai), dataDictionary));
	}

}
