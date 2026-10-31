package com.embabel.smith.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A prompt-backed action step — the skill-pack's view of an Embabel {@code PromptedActionSpec}.
 *
 * <p>Declared in {@code steps/<name>.yml}. The action declares its input type names, a single
 * output type name, and a Jinja2 prompt template that references its inputs by the
 * decapitalized input-type name (e.g. input type {@code UserInput} is referenced as
 * {@code {{userInput.content}}}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ActionStepSpec(
        @JsonProperty("stepType") String stepType,
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("inputTypeNames") List<String> inputTypeNames,
        @JsonProperty("outputTypeName") String outputTypeName,
        @JsonProperty("prompt") String prompt,
        @JsonProperty("pre") List<String> pre,
        @JsonProperty("post") List<String> post,
        @JsonProperty("cost") Double cost,
        @JsonProperty("value") Double value,
        @JsonProperty("canRerun") Boolean canRerun,
        @JsonProperty("nullable") Boolean nullable,
        @JsonProperty("toolGroups") List<String> toolGroups) {

    public static final String STEP_TYPE = "action";

    public ActionStepSpec {
        inputTypeNames = inputTypeNames == null ? List.of() : List.copyOf(inputTypeNames);
        pre = pre == null ? List.of() : List.copyOf(pre);
        post = post == null ? List.of() : List.copyOf(post);
        toolGroups = toolGroups == null ? List.of() : List.copyOf(toolGroups);
    }

    public static ActionStepSpec action(String name, String description, List<String> inputTypeNames,
                                        String outputTypeName, String prompt) {
        return new ActionStepSpec(STEP_TYPE, name, description, inputTypeNames, outputTypeName,
                prompt, null, null, null, null, null, null, null);
    }
}
