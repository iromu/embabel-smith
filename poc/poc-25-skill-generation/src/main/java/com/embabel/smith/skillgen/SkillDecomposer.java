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

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The extraction step: decomposes a business-process blueprint into the set of Agent Skills
 * required to compartmentalize the process. One LLM call asks the model to split the process
 * into coherent skills, each with a name and a self-contained scope (the part of the process it
 * owns, with its decision points, rules, and gotchas). Each scope is then generated into its own
 * {@link Skill} and eval'd independently — the same decompose-then-process pattern POC 24 uses
 * for multi-agent generation.
 *
 * @author Ivan Rodriguez
 */
public class SkillDecomposer {

	private final ChatCaller caller;

	private final String prompt;

	private final ObjectMapper json = new ObjectMapper();

	public SkillDecomposer(ChatCaller caller, String prompt) {
		this.caller = caller;
		this.prompt = prompt;
	}

	/**
	 * Decomposes the blueprint into the set of skills that compartmentalize it.
	 * @param blueprint the business-process description.
	 * @return the skills, in process order, each with a valid name and a self-contained scope.
	 */
	public List<SkillScope> decompose(String blueprint) {
		String user = "Below is a business-process description. Decompose it into the set of skills required to "
				+ "compartmentalize the process.\n\n" + blueprint;
		JsonNode node = LlmJson.callAndParse(this.caller, this.prompt, user, 3, "the skill decomposition",
				(json) -> this.json.readTree(json));
		List<SkillScope> out = new ArrayList<>();
		JsonNode skills = node.path("skills");
		if (skills.isArray()) {
			for (JsonNode s : skills) {
				String name = slug(s.path("name").asText(""));
				String scope = s.path("scope").asText("");
				if (name.isEmpty() || scope.isBlank()) {
					continue;
				}
				out.add(new SkillScope(name, scope));
			}
		}
		if (out.isEmpty()) {
			throw new IllegalStateException("Skill decomposition returned no skills");
		}
		return out;
	}

	/**
	 * Normalizes a model-supplied skill name into a valid Agent Skills name (lowercase,
	 * hyphenated, no leading/trailing hyphens).
	 * @param name the raw name.
	 * @return the slug, empty when the name has no usable characters.
	 */
	private static String slug(String name) {
		return name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
	}

	/**
	 * A single skill to generate: a valid name and a self-contained scope.
	 * @param name the skill name (kebab-case, must match the skill folder name).
	 * @param scope the self-contained description of this skill's responsibility.
	 */
	public record SkillScope(String name, String scope) {
	}

}
