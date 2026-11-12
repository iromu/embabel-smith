package com.embabel.smith.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The exported goal — the agent spec's view of an Embabel {@code GoalSpec}.
 *
 * <p>Declared in {@code goal.yml}. An agent spec exposes exactly one callable goal: it is
 * marked {@code export: true} and names a single output type. A goal that is not exported is
 * not callable, so it is invalid as an agent spec goal.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GoalStepSpec(
        @JsonProperty("stepType") String stepType,
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("outputTypeName") String outputTypeName,
        @JsonProperty("export") Boolean export) {

    public static final String STEP_TYPE = "goal";

    public boolean isExported() {
        return Boolean.TRUE.equals(export);
    }

    public static GoalStepSpec exportedGoal(String name, String description, String outputTypeName) {
        return new GoalStepSpec(STEP_TYPE, name, description, outputTypeName, true);
    }
}
