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

package com.embabel.smith.skillgen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Turns a business-process blueprint into an Agent Skill. The skill name is fixed by the
 * blueprint (so it matches the skill folder name, per the Agent Skills spec); the model
 * supplies the description (the triggering mechanism) and the body (the operating procedure),
 * returned as JSON and rendered to a {@link Skill}.
 *
 * @author Ivan Rodriguez
 */
public class SkillGenerator {

	private final ChatCaller caller;

	private final String prompt;

	private final ObjectMapper json = new ObjectMapper();

	public SkillGenerator(ChatCaller caller, String prompt) {
		this.caller = caller;
		this.prompt = prompt;
	}

	/**
	 * Generates a skill for the named blueprint.
	 * @param skillName the skill name to use (from the blueprint slug).
	 * @param blueprint the business-process description.
	 * @return the generated skill.
	 */
	public Skill generate(String skillName, String blueprint) {
		String user = "Below is a business-process description. Generate the skill for the skill named '"
				+ skillName + "'.\n\n" + blueprint;
		JsonNode node = LlmJson.callAndParse(this.caller, this.prompt, user, 3, "the generated skill",
				(json) -> this.json.readTree(json));
		String description = node.path("description").asText("");
		String body = node.path("body").asText("");
		if (description.isBlank() || body.isBlank()) {
			throw new IllegalStateException("Skill generation returned a missing description or body");
		}
		return new Skill(skillName, description, body);
	}

}
