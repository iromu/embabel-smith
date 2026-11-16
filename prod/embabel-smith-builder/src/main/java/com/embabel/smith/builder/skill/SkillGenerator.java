/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.skill;

import com.embabel.smith.builder.core.SpecPromptCaller;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Turns a business-process description into an Agent Skill. The skill name is fixed by
 * the description (so it matches the skill folder name, per the Agent Skills spec, and so
 * rebuilding the same description targets the same source location); the model supplies
 * the description — the triggering mechanism — and the body — the operating procedure —
 * returned as JSON and rendered to a {@link Skill}.
 *
 * <p>
 * The model call sits behind the builder's {@link SpecPromptCaller} seam, so parsing is
 * offline-testable with a canned answer.
 *
 * @author Ivan Rodriguez
 */
public class SkillGenerator {

	private final SpecPromptCaller caller;

	private final String prompt;

	private final ObjectMapper json = new ObjectMapper();

	public SkillGenerator(SpecPromptCaller caller, String prompt) {
		this.caller = caller;
		this.prompt = prompt;
	}

	/**
	 * Generates a skill for the named responsibility.
	 * @param skillName the skill name to use (derived from the description).
	 * @param description the business-process description.
	 * @return the generated skill.
	 * @throws IllegalStateException when the model omits the description or the body.
	 */
	public Skill generate(String skillName, String description) {
		String user = "Below is a business-process description. Generate the skill for the skill named '" + skillName
				+ "'.\n\n" + description;
		JsonNode node = LlmJson.callAndParse(this.caller, this.prompt, user, 3, "the generated skill",
				(raw) -> this.json.readTree(raw));
		String skillDescription = node.path("description").asText("");
		String body = node.path("body").asText("");
		if (skillDescription.isBlank() || body.isBlank()) {
			throw new IllegalStateException("Skill generation returned a missing description or body");
		}
		return new Skill(skillName, skillDescription, body);
	}

}
