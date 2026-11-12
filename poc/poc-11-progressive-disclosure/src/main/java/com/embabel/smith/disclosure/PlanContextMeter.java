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

package com.embabel.smith.disclosure;

import java.util.StringJoiner;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.DynamicTypeDef;

/**
 * Measures the context a spec's plan and execution carry (POC 11).
 *
 * <p>
 * Two distinct contexts, mirroring Embabel's progressive disclosure (doc 02 §2):
 * <ul>
 * <li><b>Plan context</b> — what the GOAP planner loads: the goal and each action's
 * name/description/types. It never includes the action prompt or any bundled reference,
 * so it is bounded regardless of how much reference material the spec ships.</li>
 * <li><b>Execution context</b> — the prompt an action sends to the LLM. Under
 * <em>eager</em> disclosure the bundled reference is inlined here (grows with the
 * reference); under <em>progressive</em> disclosure it is fetched on demand, so the base
 * prompt stays small.</li>
 * </ul>
 *
 * @author Ivan Rodriguez
 */
public class PlanContextMeter {

	/**
	 * The metadata the planner loads: goal + action + type names and descriptions.
	 * @param pack the agent spec to measure
	 * @return the plan context as a string
	 */
	public String planContext(AgentSpec pack) {
		StringBuilder sb = new StringBuilder();
		sb.append("goal: ").append(pack.goal().name()).append(" — ").append(pack.goal().description()).append('\n');
		for (ActionStepSpec step : pack.steps()) {
			sb.append("action: ")
				.append(step.name())
				.append(" [")
				.append(join(step.inputTypeNames()))
				.append(" -> ")
				.append(step.outputTypeName())
				.append("] ")
				.append(step.description())
				.append('\n');
		}
		for (DynamicTypeDef type : pack.types()) {
			sb.append("type: ").append(type.name()).append(" — ").append(type.description()).append('\n');
		}
		return sb.toString();
	}

	/**
	 * The plan context size in characters.
	 * @param pack the agent spec to measure
	 * @return the plan context size in characters
	 */
	public int planContextChars(AgentSpec pack) {
		return planContext(pack).length();
	}

	/**
	 * Execution context under progressive disclosure: just the base prompt (reference on
	 * demand).
	 * @param pack the agent spec to measure
	 * @return the progressive execution context size in characters
	 */
	public int progressivePromptChars(AgentSpec pack) {
		return pack.steps().stream().mapToInt((s) -> length(s.prompt())).sum();
	}

	/**
	 * Execution context under eager disclosure: the base prompt with the reference
	 * inlined.
	 * @param pack the agent spec to measure
	 * @param reference the bundled reference text
	 * @return the eager execution context size in characters
	 */
	public int eagerPromptChars(AgentSpec pack, String reference) {
		return pack.steps().stream().mapToInt((s) -> length(s.prompt()) + reference.length()).sum();
	}

	/**
	 * A rough token estimate (about 4 chars/token for English-ish text).
	 * @param chars the number of characters
	 * @return the rough token estimate
	 */
	public static int estimateTokens(int chars) {
		return chars / 4;
	}

	private static int length(String s) {
		return (s != null) ? s.length() : 0;
	}

	private static String join(java.util.List<String> names) {
		StringJoiner joiner = new StringJoiner(", ");
		names.forEach(joiner::add);
		return joiner.toString();
	}

}
