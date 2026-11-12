package com.embabel.smith.runtime;

import com.embabel.agent.core.DomainType;
import com.embabel.agent.core.DynamicType;
import com.embabel.smith.common.samples.SampleAgentSpecs;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the seam between the format owner ({@code smith-common}) and the Embabel
 * runtime: the pack's pure-data types map onto Embabel {@link DynamicType}s with their
 * own-properties intact.
 */
class AgentSpecTypeMapperTest {

	private final AgentSpecTypeMapper mapper = new AgentSpecTypeMapper();

	@Test
	void mapsIntermediateTypesToDynamicTypes() {
		List<DynamicType> types = mapper.toDynamicTypes(SampleAgentSpecs.explainer());

		assertThat(types).hasSize(1);
		DynamicType topicBrief = types.get(0);
		assertThat(topicBrief.getName()).isEqualTo("TopicBrief");
		assertThat(topicBrief.getDescription()).isEqualTo("The extracted topic, audience and tone for the explainer");
		assertThat(topicBrief.getOwnProperties()).hasSize(3);
		assertThat(topicBrief.getOwnProperties()).extracting(p -> p.getName())
			.containsExactly("topic", "audience", "tone");
		assertThat(topicBrief.getParents()).isEmpty();
	}

	@Test
	void allRuntimeTypesIncludeBuiltInIoTypesPlusThePackTypes() {
		List<DomainType> types = mapper.allRuntimeTypes(SampleAgentSpecs.explainer());

		assertThat(types).hasSize(3); // UserInput + BackgroundMessage + TopicBrief
		// JvmType registers under the fully-qualified class name, which is exactly what
		// the
		// step YAML references (e.g. com.embabel.agent.domain.io.UserInput).
		assertThat(types).extracting(DomainType::getName)
			.contains(SampleAgentSpecs.USER_INPUT, SampleAgentSpecs.BACKGROUND_MESSAGE, "TopicBrief");
	}

}
