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

package com.embabel.smith.agentspecformat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.GoalStepSpec;

/**
 * The exported-goal-as-spec primitive (POC 4).
 *
 * <p>
 * An Embabel "spec" is an <em>exported goal</em>. This resolves, for a agent spec, which
 * goal is the callable spec and traces the action chain that the planner will walk from
 * the entry type to the goal's output type. A goal declared {@code export: false} is
 * <em>not</em> a callable spec, so it is not exposed.
 *
 * @author Ivan Rodriguez
 */
public class SpecSurface {

	/**
	 * Whether the pack exposes a callable spec (an exported goal).
	 * @param pack the agent spec to inspect
	 * @return true when the pack's goal is exported
	 */
	public boolean isCallableSpec(AgentSpec pack) {
		GoalStepSpec goal = pack.goal();
		return goal != null && goal.isExported();
	}

	/**
	 * The pack's callable spec (its exported goal), or {@code null} when the goal is not
	 * exported — in which case no callable spec is exposed.
	 * @param pack the agent spec to inspect
	 * @return the exported goal, or {@code null} when it is not exported
	 */
	public GoalStepSpec callableSpec(AgentSpec pack) {
		GoalStepSpec goal = pack.goal();
		return (goal != null && goal.isExported()) ? goal : null;
	}

	/**
	 * The ordered action chain the planner walks from the entry type to the goal's output
	 * type.
	 *
	 * <p>
	 * Traces backwards from the goal's output type through the steps that produce each
	 * needed type, until it reaches a type no step produces (the entry, e.g.
	 * {@code UserInput}). Assumes a single-input chain (the common case for POC agent
	 * specs).
	 * @param pack the agent spec to trace
	 * @return the ordered action chain from entry to goal output
	 */
	public List<ActionStepSpec> actionChain(AgentSpec pack) {
		List<ActionStepSpec> chain = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		String needed = pack.goalOutputType();
		while (needed != null && !seen.contains(needed)) {
			seen.add(needed);
			ActionStepSpec producer = findProducer(pack, needed);
			if (producer == null) {
				break; // entry type reached: no step produces it
			}
			chain.add(0, producer);
			needed = producer.inputTypeNames().isEmpty() ? null : producer.inputTypeNames().get(0);
		}
		return chain;
	}

	private ActionStepSpec findProducer(AgentSpec pack, String typeName) {
		return pack.steps().stream().filter((s) -> typeName.equals(s.outputTypeName())).findFirst().orElse(null);
	}

}
