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

/**
 * An Agent Skill in the Agent Skills format: a folder whose required {@code SKILL.md} carries
 * YAML frontmatter ({@code name}, {@code description}) plus a markdown body. This record is the
 * in-memory form of that file; {@link #toSkillMd()} renders it.
 *
 * @param name the skill identifier (lowercase, hyphenated, must match the folder name)
 * @param description what the skill does and when to use it — the triggering mechanism
 * @param body the markdown instructions the agent follows once the skill activates
 * @author Ivan Rodriguez
 */
public record Skill(String name, String description, String body) {

	/**
	 * Renders the skill as a {@code SKILL.md} file: frontmatter, a blank line, then the body.
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
