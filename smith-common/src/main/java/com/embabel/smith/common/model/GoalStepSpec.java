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

package com.embabel.smith.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
/**
 * The exported goal — the agent spec's view of an Embabel {@code GoalSpec}.
 *
 * <p>
 * Declared in {@code goal.yml}. An agent spec exposes exactly one callable goal: it is
 * marked {@code export: true} and names a single output type. A goal that is not exported
 * is not callable, so it is invalid as an agent spec goal.
 *
 * @param stepType the step-type discriminator, {@code "goal"}
 * @param name the name of the goal
 * @param description a human-readable description of the goal
 * @param outputTypeName the name of the type the goal produces
 * @param export whether the goal is exported (callable)
 * @author Ivan Rodriguez
 */

public record GoalStepSpec(@JsonProperty("stepType") String stepType, @JsonProperty("name") String name,
		@JsonProperty("description") String description, @JsonProperty("outputTypeName") String outputTypeName,
		@JsonProperty("export") Boolean export) {

	/** The step-type discriminator value written to {@code stepType} in the YAML. */
	public static final String STEP_TYPE = "goal";

	public boolean isExported() {
		return Boolean.TRUE.equals(this.export);
	}

	public static GoalStepSpec exportedGoal(String name, String description, String outputTypeName) {
		return new GoalStepSpec(STEP_TYPE, name, description, outputTypeName, true);
	}
}
