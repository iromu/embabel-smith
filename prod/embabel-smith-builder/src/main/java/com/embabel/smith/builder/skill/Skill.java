/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.skill;

/**
 * An Agent Skill in the Agent Skills format: a folder whose required {@code SKILL.md}
 * carries YAML frontmatter ({@code name}, {@code description}) plus a markdown body. This
 * record is the in-memory form of that file; {@link #toSkillMd()} renders it.
 *
 * <p>
 * Unlike an {@link com.embabel.smith.common.model.AgentSpec}, a skill does not execute a
 * process — it teaches a general-purpose agent the procedure, so its value is measured as
 * the with/without-skill lift rather than a deterministic task score.
 *
 * @param name the skill identifier (lowercase, hyphenated, must match the folder name)
 * @param description what the skill does and when to use it — the triggering mechanism
 * @param body the markdown instructions the agent follows once the skill activates
 * @author Ivan Rodriguez
 */
public record Skill(String name, String description, String body) {

	/**
	 * Renders the skill as a {@code SKILL.md} file: frontmatter, a blank line, then the
	 * body.
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
