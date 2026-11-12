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

package com.embabel.smith.refinement;

import java.util.List;
import java.util.Locale;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.samples.SampleAgentSpecs;

/**
 * The LLM-authored spec under refinement (POC 9): a tagline writer whose prompt was
 * authored without the one requirement its eval tasks check — that the tagline include
 * the brand name. The weak prompt even tells the model to keep it brand-free, so the eval
 * reliably fails before the rewrite and passes after.
 *
 * @author Ivan Rodriguez
 */
public final class TaglineSpec {

	/** The step name the eval tasks target. */
	public static final String STEP_NAME = "writeTagline";

	private TaglineSpec() {
	}

	/**
	 * The weak, LLM-authored spec.
	 * @return the weak spec
	 */
	public static AgentSpec weak() {
		ActionStepSpec write = new ActionStepSpec("action", STEP_NAME, "Write a marketing tagline for a coffee brand",
				List.of(SampleAgentSpecs.USER_INPUT), SampleAgentSpecs.BACKGROUND_MESSAGE, """
						Write a short, evocative marketing tagline (one line) for the coffee brand described below.
						Keep it generic and image-focused; do not include the brand name.

						Brand brief: {{userInput.content}}
						""", null, null, null, null, null, null, List.of());
		return new AgentSpec(new AgentSpecManifest("tagline-writer", "0.1.0"), GoalStepSpec.exportedGoal("taglineDone",
				"A tagline has been written", SampleAgentSpecs.BACKGROUND_MESSAGE), List.of(write), List.of());
	}

	/**
	 * The eval tasks: the tagline must include the brand name "Nimbus".
	 * @return the eval tasks
	 */
	public static List<SpecEvalTask> tasks() {
		PredicateContains nimbus = new PredicateContains("Nimbus");
		return List.of(
				new SpecEvalTask("The brand is Nimbus, a sustainable single-origin coffee roaster.",
						"the tagline must include the brand name 'Nimbus'", nimbus),
				new SpecEvalTask("The brand is Nimbus, focused on office coffee subscriptions.",
						"the tagline must include the brand name 'Nimbus'", nimbus));
	}

	/** Case-insensitive "contains" acceptance check. */
	private record PredicateContains(String needle) implements java.util.function.Predicate<String> {
		@Override
		public boolean test(String output) {
			return output != null && output.toLowerCase(Locale.ROOT).contains(this.needle.toLowerCase(Locale.ROOT));
		}
	}

}
