/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.common.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

	/**
	 * Whether the goal is callable, read from the {@code export} component.
	 * <p>
	 * Ignored by the writer on purpose: without the annotation Jackson serializes this
	 * derived accessor as an extra {@code exported} key beside the real {@code export}
	 * one, and {@code exported} is a key neither this model nor upstream's
	 * {@code GoalSpec} implements — so every pack this repo writes would carry a key the
	 * format's own unknown-key check exists to refuse.
	 * @return true when the goal is exported and therefore callable
	 */
	@JsonIgnore
	public boolean isExported() {
		return Boolean.TRUE.equals(this.export);
	}

	public static GoalStepSpec exportedGoal(String name, String description, String outputTypeName) {
		return new GoalStepSpec(STEP_TYPE, name, description, outputTypeName, true);
	}
}
