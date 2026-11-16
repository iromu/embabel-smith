/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.core;

import java.util.Map;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.core.DataDictionary;

/**
 * The production wiring of the generation techniques: both generators share the builder's
 * single {@link Ai} model layer, so a build (and any host that reuses it, such as the
 * studio) selects a technique without changing its endpoint configuration.
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
		return Map.of(GenerationTechnique.SINGLE_SHOT, new SingleShotAgentSpecGenerator(ai),
				GenerationTechnique.EMBABEL,
				new EmbabelAgentSpecGenerator(new DefaultAgentSpecCompiler(ai), dataDictionary));
	}

}
