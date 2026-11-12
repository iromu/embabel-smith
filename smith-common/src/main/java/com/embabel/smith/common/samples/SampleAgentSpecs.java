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

package com.embabel.smith.common.samples;

import java.util.List;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.common.model.DynamicTypeDef;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.model.PropertyDef;

/**
 * Canonical sample agent specs, part of the shared contract. Used by the format module's
 * own tests and by the POCs (runtime, agent-spec-format, builder) as well-formed
 * fixtures, so every module is checked against the same sample.
 *
 * @author Ivan Rodriguez
 */
public final class SampleAgentSpecs {

	/** The fully-qualified name of the Embabel user-input entry type. */
	public static final String USER_INPUT = "com.embabel.agent.domain.io.UserInput";

	/** The fully-qualified name of the Embabel background-message output type. */
	public static final String BACKGROUND_MESSAGE = "com.embabel.agent.spec.model.BackgroundMessage";

	private SampleAgentSpecs() {
	}

	/**
	 * The two-step "explainer" agent spec:
	 * {@code UserInput -> extractTopic -> TopicBrief -> writeExplainer -> BackgroundMessage}.
	 * @return the two-step explainer agent spec
	 */
	public static AgentSpec explainer() {
		AgentSpecManifest manifest = new AgentSpecManifest("explainer", "1.0.0", "GOAP", null);

		ActionStepSpec extract = ActionStepSpec.action("extractTopic",
				"Extract the topic, audience and tone from the user's request", List.of(USER_INPUT), "TopicBrief", """
						From the user request below, extract three things:
						- topic: what they want explained
						- audience: who it is for
						- tone: the voice and style they are asking for
						User request: {{userInput.content}}
						""");

		ActionStepSpec write = ActionStepSpec.action("writeExplainer",
				"Write the explainer based on the extracted topic, audience and tone", List.of("TopicBrief"),
				BACKGROUND_MESSAGE, """
						Write a short, upbeat explainer about: {{topicBrief.topic}}
						Target audience: {{topicBrief.audience}}
						Tone: {{topicBrief.tone}}
						""");

		DynamicTypeDef topicBrief = new DynamicTypeDef("TopicBrief",
				"The extracted topic, audience and tone for the explainer",
				List.of(new PropertyDef("topic", "string", "What to explain"),
						new PropertyDef("audience", "string", "Who it is for"),
						new PropertyDef("tone", "string", "Voice and style")),
				List.of(), true);

		GoalStepSpec goal = new GoalStepSpec("goal", "explainerComplete",
				"A complete explainer has been produced for the user", BACKGROUND_MESSAGE, true);

		return new AgentSpec(manifest, goal, List.of(extract, write), List.of(topicBrief));
	}

	/**
	 * A single-step agent spec with no intermediate types (round-trips with an empty
	 * {@code types/}).
	 * @return the single-step agent spec
	 */
	public static AgentSpec singleStep() {
		AgentSpecManifest manifest = new AgentSpecManifest("trivial", "0.1.0");
		GoalStepSpec goal = new GoalStepSpec("goal", "done", "A single-step agent spec", BACKGROUND_MESSAGE, true);
		ActionStepSpec say = ActionStepSpec.action("say", "Say the input back", List.of(USER_INPUT), BACKGROUND_MESSAGE,
				"Repeat: {{userInput.content}}");
		return new AgentSpec(manifest, goal, List.of(say), List.of());
	}

}
