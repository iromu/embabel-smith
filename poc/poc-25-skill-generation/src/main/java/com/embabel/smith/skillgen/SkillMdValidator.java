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

/**
 * Checks a generated skill against the Agent Skills frontmatter constraints, so a generated
 * {@code SKILL.md} is well-formed before it is written and eval'd. Mirrors the published
 * specification: {@code name} is 1-64 lowercase-alphanumeric-or-hyphen characters, not
 * leading/trailing/consecutive hyphens; {@code description} is non-empty and at most 1024
 * characters.
 *
 * @author Ivan Rodriguez
 */
public final class SkillMdValidator {

	private SkillMdValidator() {
	}

	/**
	 * Validates the frontmatter fields.
	 * @param name the skill name to check.
	 * @param description the skill description to check.
	 * @return the list of constraint violations, empty when the skill is well-formed.
	 */
	public static List<String> validate(String name, String description) {
		List<String> issues = new ArrayList<>();
		validateName(name, issues);
		validateDescription(description, issues);
		return issues;
	}

	private static void validateName(String name, List<String> issues) {
		if (name == null || name.isEmpty()) {
			issues.add("name is missing");
			return;
		}
		if (name.length() > 64) {
			issues.add("name exceeds 64 characters (" + name.length() + ")");
		}
		if (!name.matches("[a-z0-9-]+")) {
			issues.add("name has characters other than lowercase a-z, 0-9, or hyphen: '" + name + "'");
		}
		if (name.startsWith("-")) {
			issues.add("name starts with a hyphen");
		}
		if (name.endsWith("-")) {
			issues.add("name ends with a hyphen");
		}
		if (name.contains("--")) {
			issues.add("name contains consecutive hyphens");
		}
	}

	private static void validateDescription(String description, List<String> issues) {
		if (description == null || description.isBlank()) {
			issues.add("description is empty");
			return;
		}
		if (description.length() > 1024) {
			issues.add("description exceeds 1024 characters (" + description.length() + ")");
		}
	}

}
