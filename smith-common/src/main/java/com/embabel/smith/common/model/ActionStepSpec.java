/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.common.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
/**
 * A prompt-backed action step — the agent spec's view of an Embabel
 * {@code PromptedActionSpec}.
 *
 * <p>
 * Declared in {@code steps/<name>.yml}. The action declares its input type names, a
 * single output type name, and a Jinja2 prompt template that references its inputs by the
 * decapitalized input-type name (e.g. input type {@code UserInput} is referenced as
 * {@code {{userInput.content}}}).
 * @param stepType the step-type discriminator, {@code "action"}
 * @param name the name of the action
 * @param description a human-readable description of the action
 * @param inputTypeNames the names of the types the action consumes
 * @param outputTypeName the name of the type the action produces
 * @param prompt the Jinja2 prompt template of the action
 * @param pre the precondition condition names of the action
 * @param post the postcondition condition names of the action
 * @param cost the cost of the action, or {@code null}
 * @param value the value of the action, or {@code null}
 * @param canRerun whether the action can be re-run, or {@code null}
 * @param nullable whether the action's output may be null, or {@code null}
 * @param toolGroups the tool groups the action may use, or {@code null}
 * @param references the names of the references — the skills the pack carries — that the
 * action asks to be in play for its model call, or {@code null}. This is the framework's
 * own step-spec key: a request for a capability, not a grant of reach, not a tool group,
 * and no trust claim of its own. A step declaring none is offered nothing from the pack's
 * skills.
 * @author Ivan Rodriguez
 */

public record ActionStepSpec(@JsonProperty("stepType") String stepType, @JsonProperty("name") String name,
		@JsonProperty("description") String description, @JsonProperty("inputTypeNames") List<String> inputTypeNames,
		@JsonProperty("outputTypeName") String outputTypeName, @JsonProperty("prompt") String prompt,
		@JsonProperty("pre") List<String> pre, @JsonProperty("post") List<String> post,
		@JsonProperty("cost") Double cost, @JsonProperty("value") Double value,
		@JsonProperty("canRerun") Boolean canRerun, @JsonProperty("nullable") Boolean nullable,
		@JsonProperty("toolGroups") List<String> toolGroups, @JsonProperty("references") List<String> references) {

	/** The step-type discriminator value written to {@code stepType} in the YAML. */
	public static final String STEP_TYPE = "action";

	public ActionStepSpec {
		inputTypeNames = (inputTypeNames != null) ? List.copyOf(inputTypeNames) : List.of();
		pre = (pre != null) ? List.copyOf(pre) : List.of();
		post = (post != null) ? List.copyOf(post) : List.of();
		toolGroups = (toolGroups != null) ? List.copyOf(toolGroups) : List.of();
		references = (references != null) ? List.copyOf(references) : List.of();
	}

	/**
	 * The pre-references form of the canonical constructor, for call sites that have no
	 * references to declare. It delegates with an empty {@code references} so the step
	 * means exactly what it meant before the component existed — offered nothing from the
	 * pack's skills.
	 * <p>
	 * A step reconstructed field by field from an existing step must carry
	 * {@code references} forward through the canonical constructor rather than call this
	 * one: dropping the declaration is the failure the format's round-trip requirement
	 * exists to prevent.
	 */
	public ActionStepSpec(String stepType, String name, String description, List<String> inputTypeNames,
			String outputTypeName, String prompt, List<String> pre, List<String> post, Double cost, Double value,
			Boolean canRerun, Boolean nullable, List<String> toolGroups) {
		this(stepType, name, description, inputTypeNames, outputTypeName, prompt, pre, post, cost, value, canRerun,
				nullable, toolGroups, List.of());
	}

	public static ActionStepSpec action(String name, String description, List<String> inputTypeNames,
			String outputTypeName, String prompt) {
		return new ActionStepSpec(STEP_TYPE, name, description, inputTypeNames, outputTypeName, prompt, null, null,
				null, null, null, null, null, null);
	}
}
