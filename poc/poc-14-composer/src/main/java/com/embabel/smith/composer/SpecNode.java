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

package com.embabel.smith.composer;

import java.util.LinkedHashSet;
import java.util.Set;

import com.embabel.smith.common.model.AgentSpec;

/**
 * A node on the composition canvas: one spec (an exported goal + its action steps).
 *
 * <p>
 * A spec's <em>external</em> surface is what the canvas reasons about. Its <b>output</b>
 * is the exported goal's output type. Its <b>external inputs</b> are the action input
 * types that are not produced by any other action in the same spec — i.e. the type(s) the
 * spec needs from the outside (for a single-action spec, just that action's input type).
 * The drag-and-drop "snap" is exactly "spec A's output type is one of spec B's external
 * inputs".
 *
 * @author Ivan Rodriguez
 */
public final class SpecNode {

	private final String id;

	private final AgentSpec pack;

	public SpecNode(AgentSpec pack) {
		this.pack = pack;
		this.id = pack.name();
	}

	public String id() {
		return this.id;
	}

	public AgentSpec pack() {
		return this.pack;
	}

	/**
	 * The output type of the spec's exported goal.
	 * @return the output type of the spec's exported goal.
	 */
	public String outputType() {
		return this.pack.goal().outputTypeName();
	}

	/**
	 * The types the spec consumes from outside itself: every action input type minus
	 * every type an action in the spec produces. Normally a single type (the spec's entry
	 * type).
	 * @return the external input types the spec consumes from outside itself.
	 */
	public Set<String> externalInputTypes() {
		Set<String> produced = new LinkedHashSet<>();
		Set<String> inputs = new LinkedHashSet<>();
		this.pack.steps().forEach((step) -> {
			if (step.outputTypeName() != null) {
				produced.add(step.outputTypeName());
			}
			inputs.addAll(step.inputTypeNames());
		});
		inputs.removeAll(produced);
		return inputs;
	}

	@Override
	public String toString() {
		return this.id + " (in: " + externalInputTypes() + " -> out: " + outputType() + ")";
	}

}
