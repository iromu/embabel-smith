/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.skillgen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Turns a business-process blueprint into an Agent Skill. The skill name is fixed by the
 * blueprint (so it matches the skill folder name, per the Agent Skills spec); the model
 * supplies the description (the triggering mechanism) and the body (the operating
 * procedure), returned as JSON and rendered to a {@link Skill}.
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
		String user = "Below is a business-process description. Generate the skill for the skill named '" + skillName
				+ "'.\n\n" + blueprint;
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
