/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.skill;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.embabel.smith.builder.core.PromptData;
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

	/**
	 * How many inspection findings one redraft request carries. The bound is here, on the
	 * class that applies it, because it protects the prompt and nothing else: a
	 * pathological draft can report findings without limit, and the build report is not
	 * capped by it.
	 */
	static final int MAX_FINDINGS_IN_PROMPT = 20;

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
		return generate(skillName, description, List.of());
	}

	/**
	 * Redrafts the skill, telling the model what the inspection gate refused in the
	 * previous draft. The original instruction block is sent unchanged — the findings are
	 * appended to it, so a redraft is asked to fix the named findings and not to answer a
	 * different question.
	 * @param skillName the skill name to use (derived from the description).
	 * @param description the business-process description.
	 * @param blockingFindings the previous round's blocking findings, in report order;
	 * empty asks for a first draft
	 * @return the generated skill, carrying any scripts the model wrote
	 * @throws IllegalStateException when the model omits the description or the body, or
	 * names a script that is not a JavaScript file.
	 */
	public Skill generate(String skillName, String description, List<String> blockingFindings) {
		String user = "Below is a business-process description. Generate the skill for the skill named '" + skillName
				+ "'.\n\n" + PromptData.block("document", description) + redraftFindings(blockingFindings);
		JsonNode node = LlmJson.callAndParse(this.caller, this.prompt, user, 3, "the generated skill",
				(raw) -> this.json.readTree(raw));
		String skillDescription = node.path("description").asText("");
		String body = node.path("body").asText("");
		if (skillDescription.isBlank() || body.isBlank()) {
			throw new IllegalStateException("Skill generation returned a missing description or body");
		}
		return new Skill(skillName, skillDescription, body, scripts(node.path("scripts")));
	}

	/**
	 * The scripts the generated answer carries. A missing or empty {@code scripts} member
	 * is the ordinary case and yields no scripts at all — a skill is instructions-only
	 * unless the model had something worth computing.
	 * <p>
	 * A declared path is reduced to its file name by {@link SkillScript#of}, so a model
	 * cannot address a file outside the skill's own {@code scripts/} folder. A blank
	 * script is dropped rather than refused: an empty file is not a script and refusing a
	 * whole build for one buys nothing, whereas a name that is not a {@code .js} file
	 * name stops the build at {@code generate} — the engine could never run what it
	 * names, and dropping it quietly would leave the body pointing at a file that was
	 * never written.
	 * @param scripts the {@code scripts} member of the generated JSON.
	 * @return the scripts, in the order the model declared them, deduplicated by path.
	 * @throws IllegalStateException when a declared script name is not a {@code .js}
	 * file.
	 */
	private static List<SkillScript> scripts(JsonNode scripts) {
		if (!scripts.isArray() || scripts.isEmpty()) {
			return List.of();
		}
		Map<String, SkillScript> byPath = new LinkedHashMap<>();
		for (JsonNode script : scripts) {
			String content = script.path("content").asText("");
			if (content.isBlank()) {
				continue;
			}
			String declaredPath = script.path("path").asText(script.path("name").asText(""));
			try {
				SkillScript parsed = SkillScript.of(declaredPath, content.strip() + "\n");
				byPath.put(parsed.path(), parsed);
			}
			catch (IllegalArgumentException ex) {
				throw new IllegalStateException(
						"Skill generation returned a script that is not a JavaScript file: " + ex.getMessage(), ex);
			}
		}
		return List.copyOf(byPath.values());
	}

	/**
	 * The redraft instruction and the findings it names, or nothing for a first draft.
	 * Findings beyond {@link #MAX_FINDINGS_IN_PROMPT} are counted rather than quoted, so
	 * the model is not told the list was the whole verdict.
	 * @param blockingFindings the previous round's blocking findings
	 * @return the text to append to the generation request, empty for a first draft
	 */
	private static String redraftFindings(List<String> blockingFindings) {
		if (blockingFindings.isEmpty()) {
			return "";
		}
		List<String> quoted = blockingFindings.stream().limit(MAX_FINDINGS_IN_PROMPT).toList();
		String findings = String.join("\n", quoted);
		int omitted = blockingFindings.size() - quoted.size();
		if (omitted > 0) {
			findings = findings + "\n(and " + omitted + " more findings of the same kind)";
		}
		return "\n\nA previous draft of this skill was refused by the skill inspection gate. Rewrite it so every "
				+ "finding below is resolved, keeping what the earlier draft already got right.\n\n"
				+ PromptData.block("findings", findings);
	}

}
