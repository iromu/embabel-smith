/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.skill;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * An Agent Skill in the Agent Skills format: a folder whose required {@code SKILL.md}
 * carries YAML frontmatter ({@code name}, {@code description}) plus a markdown body, and
 * whose optional {@code scripts/} folder carries JavaScript the reader's agent can run.
 * This record is the in-memory form of that folder; {@link #toSkillMd()} renders the file
 * and {@link #scripts()} the folder.
 *
 * <p>
 * Unlike an {@link com.embabel.smith.common.model.AgentSpec}, a skill does not execute a
 * process — it teaches a general-purpose agent the procedure, so its value is measured as
 * the with/without-skill lift rather than a deterministic task score.
 *
 * <p>
 * The two halves are deliberately independent: {@link #toSkillMd()} does not look at the
 * scripts, so a skill that carries none renders and publishes exactly as it did before
 * scripts existed. A skill that carries them publishes the same three files plus
 * {@code scripts/}, which is what makes the executable surface an addition rather than a
 * change of contract.
 *
 * @param name the skill identifier (lowercase, hyphenated, must match the folder name)
 * @param description what the skill does and when to use it — the triggering mechanism
 * @param body the markdown instructions the agent follows once the skill activates
 * @param scripts the JavaScript files the skill carries, empty for an instructions-only
 * skill; each is addressed at {@code scripts/<name>.js} inside the folder
 * @author Ivan Rodriguez
 */
public record Skill(String name, String description, String body, List<SkillScript> scripts) {

	public Skill {
		scripts = (scripts != null) ? List.copyOf(scripts) : List.of();
	}

	/**
	 * An instructions-only skill: the shape that predates script authoring, and the shape
	 * a build produces whenever the model writes no script.
	 * @param name the skill identifier.
	 * @param description what the skill does and when to use it.
	 * @param body the markdown instructions.
	 */
	public Skill(String name, String description, String body) {
		this(name, description, body, List.of());
	}

	/**
	 * Renders the skill as a {@code SKILL.md} file: frontmatter, a blank line, then the
	 * body. Scripts are files in the folder, not frontmatter fields, so they appear here
	 * only as the paths the body tells the agent to run.
	 * @return the SKILL.md content.
	 */
	public String toSkillMd() {
		StringBuilder sb = new StringBuilder();
		sb.append("---\n");
		sb.append("name: ").append(this.name).append("\n");
		sb.append("description: ").append(quoteYaml(this.description)).append("\n");
		sb.append("---\n\n");
		sb.append((this.body == null) ? "" : this.body.trim()).append("\n");
		return sb.toString();
	}

	/**
	 * The scripts addressed by path, the form the security scan reads: a script that
	 * passes is one the scan saw at the path it will be published to. The map is keyed in
	 * path order, not the model's order, because the scan's findings go into the build
	 * report and a report that reordered them between two identical builds would be
	 * unreadable.
	 * @return the path-to-source map of this skill's scripts, in path order.
	 */
	public Map<String, String> scriptSources() {
		return this.scripts.stream()
			.collect(
					Collectors.toMap(SkillScript::path, SkillScript::content, (first, second) -> second, TreeMap::new));
	}

	/**
	 * The description is model-supplied, so it may carry YAML metacharacters
	 * ({@code ": "}, a leading {@code #}) or newlines that would otherwise break the
	 * frontmatter it is written into and leave a published skill unloadable. A
	 * double-quoted scalar with the control characters escaped keeps every accepted
	 * description loadable. The name needs no quoting: it is a
	 * {@link com.embabel.smith.builder.core.PackName} slug.
	 * @param value the description to render.
	 * @return the value as a double-quoted YAML scalar.
	 */
	private static String quoteYaml(String value) {
		if (value == null) {
			return "\"\"";
		}
		StringBuilder quoted = new StringBuilder(value.length() + 2).append('"');
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
				case '"' -> quoted.append("\\\"");
				case '\\' -> quoted.append("\\\\");
				case '\n' -> quoted.append("\\n");
				case '\r' -> quoted.append("\\r");
				case '\t' -> quoted.append("\\t");
				default -> {
					if (c < 0x20) {
						quoted.append(String.format("\\u%04x", (int) c));
					}
					else {
						quoted.append(c);
					}
				}
			}
		}
		return quoted.append('"').toString();
	}

}
