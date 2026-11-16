/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.skillgen;

/**
 * An Agent Skill in the Agent Skills format: a folder whose required {@code SKILL.md}
 * carries YAML frontmatter ({@code name}, {@code description}) plus a markdown body. This
 * record is the in-memory form of that file; {@link #toSkillMd()} renders it.
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
		sb.append("description: ").append(this.description).append("\n");
		sb.append("---\n\n");
		sb.append((this.body == null) ? "" : this.body.trim()).append("\n");
		return sb.toString();
	}

}
