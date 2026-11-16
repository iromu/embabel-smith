/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.core;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.core.AgentPlatform;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Offline tests of {@link AgentSpecGenerators#production}: the technique-keyed map must
 * hold one entry per technique and reach the generator that actually implements it. Both
 * the build default and the studio's technique selector resolve through this map, so a
 * mis-keyed entry would silently run the wrong technique. The model layer is mocked, so
 * nothing here touches an endpoint.
 */
class AgentSpecGeneratorsTest {

	@Test
	void productionMapHoldsEveryTechnique() {
		Map<GenerationTechnique, AgentSpecGenerator> generators = AgentSpecGenerators.production(mock(Ai.class),
				mock(AgentPlatform.class));

		assertThat(generators).hasSize(GenerationTechnique.values().length);
		assertThat(generators.keySet()).containsExactlyInAnyOrder(GenerationTechnique.values());
	}

	@Test
	void eachTechniqueReachesItsOwnGenerator() {
		Map<GenerationTechnique, AgentSpecGenerator> generators = AgentSpecGenerators.production(mock(Ai.class),
				mock(AgentPlatform.class));

		assertThat(generators.get(GenerationTechnique.SINGLE_SHOT)).isInstanceOf(SingleShotAgentSpecGenerator.class);
		assertThat(generators.get(GenerationTechnique.EMBABEL)).isInstanceOf(EmbabelAgentSpecGenerator.class);
	}

}
