package com.embabel.smith.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The exported goal — the skill-pack's view of an Embabel {@code GoalSpec}.
 *
 * <p>Declared in {@code goal.yml}. A skill-pack exposes exactly one callable skill, and that
 * skill <em>is</em> this goal: it is marked {@code export: true} and names a single output
 * type. A goal that is not exported does not expose a callable skill, so it is invalid as a
 * skill-pack goal.
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
