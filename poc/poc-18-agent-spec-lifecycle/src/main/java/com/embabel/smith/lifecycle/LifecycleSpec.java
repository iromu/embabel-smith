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

package com.embabel.smith.lifecycle;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.samples.SampleAgentSpecs;

/**
 * The demo spec for POC 18: a tagline writer with three variants that differ <em>only in
 * prompt</em> — same goal, same I/O types — so any drift is attributable to the prompt
 * or behavior.
 *
 * <ul>
 * <li><em>healthy</em> — the prompt requires the brand name, so its eval passes
 * (~1.0);</li>
 * <li><em>reworded</em> — the healthy prompt with a cosmetic edit (behavior unchanged,
 * eval still passes, but the content hash differs → pure <em>content drift</em>);</li>
 * <li><em>regressed</em> — the prompt forbids the brand name, so its eval fails (~0.0) →
 * content and score drift, the "spec silently breaks" scenario.</li>
 * </ul>
 *
 * @author Ivan Rodriguez
 */
public final class LifecycleSpec {

	/** The spec id for the demo tagline writer. */
	public static final String SPEC_ID = "tagline-writer";

	/** The shared acceptance criterion: the tagline must name the brand "Nimbus". */
	private static final String ACCEPTANCE = "the tagline must include the brand name 'Nimbus'";

	private LifecycleSpec() {
	}

	public static AgentSpec healthy() {
		return pack("Write a short marketing tagline that explicitly includes the brand name.\n\n"
				+ "Brand brief: {{userInput.content}}");
	}

	public static AgentSpec reworded() {
		return pack("Write a brief marketing tagline that explicitly includes the brand name.\n\n"
				+ "Brand brief: {{userInput.content}}");
	}

	public static AgentSpec regressed() {
		return pack("Write a short, generic marketing tagline. Do NOT include the brand name.\n\n"
				+ "Brand brief: {{userInput.content}}");
	}

	private static AgentSpec pack(String prompt) {
		ActionStepSpec write = ActionStepSpec.action("writeTagline", "Write a marketing tagline for a coffee brand",
				List.of(SampleAgentSpecs.USER_INPUT), SampleAgentSpecs.BACKGROUND_MESSAGE, prompt);
		return new AgentSpec(new AgentSpecManifest(SPEC_ID, "1.0.0"), GoalStepSpec.exportedGoal("taglineDone",
				"A tagline has been written", SampleAgentSpecs.BACKGROUND_MESSAGE), List.of(write), List.of());
	}

	/**
	 * The eval tasks: the tagline must include the brand name "Nimbus".
	 * @return the eval tasks for the spec.
	 */
	public static List<SpecEvalTask> tasks() {
		Predicate<String> containsNimbus = (out) -> out != null && out.toLowerCase(Locale.ROOT).contains("nimbus");
		return List.of(
				new SpecEvalTask("The brand is Nimbus, a sustainable single-origin coffee roaster.", ACCEPTANCE,
						containsNimbus),
				new SpecEvalTask("The brand is Nimbus, focused on office coffee subscriptions.", ACCEPTANCE,
						containsNimbus));
	}

}
