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

package com.embabel.smith.skillscripts;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.embabel.agent.skills.script.ScriptLanguage;
import com.embabel.agent.skills.script.SkillScript;

/**
 * Test helpers: write a one-off skill folder and address a script inside it.
 * @author Ivan Rodriguez
 */
final class SkillFixtures {

	private SkillFixtures() {
	}

	/**
	 * Create a skill folder containing a single script.
	 * @param name the skill name (must match the folder name)
	 * @param fileName the script file name, e.g. {@code run.js}
	 * @param source the script source
	 * @return the skill's base directory.
	 */
	static Path skillWithScript(String name, String fileName, String source) {
		try {
			Path dir = Files.createTempDirectory("poc26-" + name + "-");
			Files.createDirectories(dir.resolve("scripts"));
			Files.writeString(dir.resolve("SKILL.md"), """
					---
					name: %s
					description: Test fixture skill for POC 26.
					---

					Runs %s.
					""".formatted(name, fileName), StandardCharsets.UTF_8);
			Files.writeString(dir.resolve("scripts").resolve(fileName), source, StandardCharsets.UTF_8);
			return dir;
		}
		catch (Exception ex) {
			throw new IllegalStateException("Could not build the test skill '" + name + "': " + ex.getMessage(), ex);
		}
	}

	/**
	 * @param dir the skill's base directory
	 * @param fileName the script file name
	 * @return the script handle to hand an engine.
	 */
	static SkillScript script(Path dir, String fileName) {
		return new SkillScript(dir.getFileName().toString(), fileName, ScriptLanguage.JAVASCRIPT, dir);
	}

	/**
	 * The bundled skills that ship with the POC, as a filesystem directory.
	 * @return the directory holding the sample skill folders.
	 */
	static Path bundledSkillsDir() {
		Path dir = Path.of("").toAbsolutePath();
		while (dir != null) {
			Path candidate = dir.resolve("src/main/resources/skills");
			if (Files.isDirectory(candidate)) {
				return candidate;
			}
			if (Files.isDirectory(dir.resolve("poc/poc-26-graaljs-skill-scripts/src/main/resources/skills"))) {
				return dir.resolve("poc/poc-26-graaljs-skill-scripts/src/main/resources/skills");
			}
			dir = dir.getParent();
		}
		throw new IllegalStateException("Could not locate the module's src/main/resources/skills directory");
	}

}
