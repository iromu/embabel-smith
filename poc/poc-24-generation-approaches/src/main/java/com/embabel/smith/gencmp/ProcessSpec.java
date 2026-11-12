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

package com.embabel.smith.gencmp;

import java.util.List;

import com.embabel.smith.common.model.AgentSpec;

/**
 * A business process decomposed into one or more {@link AgentSpec} agents, in the order the
 * process runs. The first agent takes the operator's {@code UserInput}; the last agent's
 * goal is the process's final deliverable; agents hand off to each other through shared
 * intermediate types. A single-agent process is the flat baseline.
 *
 * @param process the process name
 * @param agents the agents of the process, in run order
 * @author Ivan Rodriguez
 */
public record ProcessSpec(String process, List<AgentSpec> agents) {

	public ProcessSpec {
		agents = (agents != null) ? List.copyOf(agents) : List.of();
	}

	public static ProcessSpec single(String process, AgentSpec agent) {
		return new ProcessSpec(process, List.of(agent));
	}

	public int totalSteps() {
		return this.agents.stream().mapToInt((a) -> a.steps().size()).sum();
	}

	/** The last agent's goal output type — the process's final deliverable. */
	public String finalOutputType() {
		AgentSpec last = this.agents.get(this.agents.size() - 1);
		return (last.goal() != null) ? last.goal().outputTypeName() : null;
	}

}
