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
 *
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
 * @author Ivan Rodriguez
 */

public record ActionStepSpec(@JsonProperty("stepType") String stepType, @JsonProperty("name") String name,
		@JsonProperty("description") String description, @JsonProperty("inputTypeNames") List<String> inputTypeNames,
		@JsonProperty("outputTypeName") String outputTypeName, @JsonProperty("prompt") String prompt,
		@JsonProperty("pre") List<String> pre, @JsonProperty("post") List<String> post,
		@JsonProperty("cost") Double cost, @JsonProperty("value") Double value,
		@JsonProperty("canRerun") Boolean canRerun, @JsonProperty("nullable") Boolean nullable,
		@JsonProperty("toolGroups") List<String> toolGroups) {

	/** The step-type discriminator value written to {@code stepType} in the YAML. */
	public static final String STEP_TYPE = "action";

	public ActionStepSpec {
		inputTypeNames = (inputTypeNames != null) ? List.copyOf(inputTypeNames) : List.of();
		pre = (pre != null) ? List.copyOf(pre) : List.of();
		post = (post != null) ? List.copyOf(post) : List.of();
		toolGroups = (toolGroups != null) ? List.copyOf(toolGroups) : List.of();
	}

	public static ActionStepSpec action(String name, String description, List<String> inputTypeNames,
			String outputTypeName, String prompt) {
		return new ActionStepSpec(STEP_TYPE, name, description, inputTypeNames, outputTypeName, prompt, null, null,
				null, null, null, null, null);
	}
}
