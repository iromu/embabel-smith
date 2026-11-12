package com.embabel.smith.agentspecformat;

import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.samples.SampleAgentSpecs;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies tasks 4.1 (offline part) and 4.2: an exported goal is the callable spec and
 * its action chain resolves to the goal's output type; a goal declared
 * {@code export: false} is not exposed.
 */
class SpecSurfaceTest {

	private final SpecSurface surface = new SpecSurface();

	@Test
	void anExportedGoalIsACallableSpec() {
		AgentSpec pack = SampleAgentSpecs.explainer();

		assertThat(surface.isCallableSpec(pack)).isTrue();
		GoalStepSpec spec = surface.callableSpec(pack);
		assertThat(spec).isNotNull();
		assertThat(spec.name()).isEqualTo("explainerComplete");
		assertThat(spec.outputTypeName()).isEqualTo(SampleAgentSpecs.BACKGROUND_MESSAGE);
	}

	@Test
	void aNonExportedGoalIsNotExposedAsACallableSpec() {
		AgentSpec pack = SampleAgentSpecs.explainer();
		AgentSpec notExported = new AgentSpec(pack.manifest(), new GoalStepSpec("goal", "explainerComplete",
				"A complete explainer", SampleAgentSpecs.BACKGROUND_MESSAGE, false), pack.steps(), pack.types());

		assertThat(surface.isCallableSpec(notExported)).isFalse();
		assertThat(surface.callableSpec(notExported)).isNull();
	}

	@Test
	void theActionChainResolvesFromEntryToTheGoalOutput() {
		AgentSpec pack = SampleAgentSpecs.explainer();

		List<com.embabel.smith.common.model.ActionStepSpec> chain = surface.actionChain(pack);

		assertThat(chain).extracting(com.embabel.smith.common.model.ActionStepSpec::name)
			.containsExactly("extractTopic", "writeExplainer");
		// the chain's final output is the goal's output type
		assertThat(chain.get(chain.size() - 1).outputTypeName()).isEqualTo(pack.goalOutputType());
	}

}
