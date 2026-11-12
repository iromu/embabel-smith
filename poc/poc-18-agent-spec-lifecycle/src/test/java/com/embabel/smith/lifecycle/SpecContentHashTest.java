package com.embabel.smith.lifecycle;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.DynamicTypeDef;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.model.PropertyDef;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.common.samples.SampleAgentSpecs;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline checks for the content hash: it is stable for an unchanged pack and changes
 * when any load-bearing surface (prompt, referenced type, tool group) changes — the
 * content-drift signal.
 */
class SpecContentHashTest {

	@Test
	void isStableForAnUnchangedPack() {
		AgentSpec pack = LifecycleSpec.healthy();
		assertThat(SpecContentHash.of(pack)).isEqualTo(SpecContentHash.of(pack));
		assertThat(SpecContentHash.of(pack)).hasSize(64); // SHA-256 hex
	}

	@Test
	void changesWhenThePromptChanges() {
		assertThat(SpecContentHash.of(LifecycleSpec.healthy()))
			.isNotEqualTo(SpecContentHash.of(LifecycleSpec.reworded()));
		assertThat(SpecContentHash.of(LifecycleSpec.healthy()))
			.isNotEqualTo(SpecContentHash.of(LifecycleSpec.regressed()));
	}

	@Test
	void changesWhenAReferencedTypeChanges() {
		AgentSpec base = singleStepWith(new DynamicTypeDef("TopicBrief", "x",
				List.of(new PropertyDef("topic", "string", "t")), List.of(), true));
		AgentSpec renamed = singleStepWith(new DynamicTypeDef("TopicBrief", "x",
				List.of(new PropertyDef("topic", "integer", "t")), List.of(), true));
		assertThat(SpecContentHash.of(base)).isNotEqualTo(SpecContentHash.of(renamed));
	}

	@Test
	void changesWhenAToolGroupChanges() {
		AgentSpec noTools = singleStep(List.of());
		AgentSpec withShell = singleStep(List.of("shell"));
		assertThat(SpecContentHash.of(noTools)).isNotEqualTo(SpecContentHash.of(withShell));
	}

	private AgentSpec singleStepWith(DynamicTypeDef type) {
		ActionStepSpec step = ActionStepSpec.action("s", "d", List.of(SampleAgentSpecs.USER_INPUT), "TopicBrief", "p");
		return new AgentSpec(new AgentSpecManifest("x", "1.0.0"), GoalStepSpec.exportedGoal("g", "d", "TopicBrief"),
				List.of(step), List.of(type));
	}

	private AgentSpec singleStep(List<String> toolGroups) {
		ActionStepSpec step = new ActionStepSpec(ActionStepSpec.STEP_TYPE, "s", "d",
				List.of(SampleAgentSpecs.USER_INPUT), SampleAgentSpecs.BACKGROUND_MESSAGE, "p", null, null, null, null,
				null, null, toolGroups);
		return new AgentSpec(new AgentSpecManifest("x", "1.0.0"),
				GoalStepSpec.exportedGoal("g", "d", SampleAgentSpecs.BACKGROUND_MESSAGE), List.of(step), List.of());
	}

}
