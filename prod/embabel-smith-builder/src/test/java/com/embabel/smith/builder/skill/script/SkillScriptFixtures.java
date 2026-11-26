/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.skill.script;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.skills.Skills;
import com.embabel.agent.skills.script.ScriptLanguage;
import com.embabel.agent.skills.script.SkillScript;
import com.embabel.agent.skills.script.SkillScriptExecutionEngine;
import com.embabel.agent.skills.support.ClaudeFrontMatterFormatter;
import com.embabel.agent.skills.support.DefaultDirectorySkillDefinitionLoader;
import com.embabel.agent.skills.support.GitHubSkillDefinitionLoader;

/**
 * Test helpers: a one-off skill folder carrying a script, addressed both as a
 * {@link SkillScript} (engine-level tests) and as a {@link Skills} pack (the tool-surface
 * tests, which are the ones that prove what the model is offered).
 *
 * @author Ivan Rodriguez
 */
final class SkillScriptFixtures {

	private SkillScriptFixtures() {
	}

	/**
	 * Create a parent directory holding one skill folder with one script.
	 * @param skillName the skill name, which must equal its folder name
	 * @param fileName the script file name, e.g. {@code run.js}
	 * @param source the script source
	 * @return the directory that a {@link Skills} pack can be loaded from.
	 */
	static Path skillPack(String skillName, String fileName, String source) {
		try {
			Path root = Files.createTempDirectory("smith-skill-script-");
			Path skill = root.resolve(skillName);
			Files.createDirectories(skill.resolve("scripts"));
			Files.writeString(skill.resolve("SKILL.md"), """
					---
					name: %s
					description: Test fixture skill for the builder's script engine.
					---

					Runs %s.
					""".formatted(skillName, fileName), StandardCharsets.UTF_8);
			Files.writeString(skill.resolve("scripts").resolve(fileName), source, StandardCharsets.UTF_8);
			return root;
		}
		catch (Exception ex) {
			throw new IllegalStateException("Could not build the test skill '" + skillName + "': " + ex.getMessage(),
					ex);
		}
	}

	/**
	 * Address a script inside a fixture skill, the way Embabel's loader would.
	 * @param packRoot the directory {@link #skillPack} returned
	 * @param skillName the skill folder name
	 * @param fileName the script file name
	 * @return the script handle to hand an engine
	 */
	static SkillScript script(Path packRoot, String skillName, String fileName) {
		return new SkillScript(skillName, fileName, ScriptLanguage.JAVASCRIPT, packRoot.resolve(skillName));
	}

	/**
	 * Load a fixture pack with an engine installed — the one line that turns
	 * {@code scripts/} into tools.
	 * @param packRoot the directory {@link #skillPack} returned
	 * @param engine the engine to install
	 * @return the skills reference an agent would be given
	 */
	static Skills load(Path packRoot, SkillScriptExecutionEngine engine) {
		return new Skills("smith-script-test", "Fixture skill pack", List.of(),
				new DefaultDirectorySkillDefinitionLoader(true), new GitHubSkillDefinitionLoader(),
				ClaudeFrontMatterFormatter.INSTANCE)
			.withLocalSkills(packRoot.toString())
			.withScriptExecutionEngine(engine);
	}

	/**
	 * The tool names the model would be offered for a pack.
	 * @param skills the loaded pack
	 * @return the tool names, in the order Embabel exposes them
	 */
	static List<String> toolNames(Skills skills) {
		return skills.tools().stream().map((tool) -> tool.getDefinition().getName()).toList();
	}

	/**
	 * Call a tool through the same entry point the platform uses, so the model's view and
	 * the test's view are the same code path.
	 * @param skills the loaded pack
	 * @param toolName the tool to call
	 * @param inputJson the tool input, in the {@code {args, stdin, inputFiles}} shape
	 * @return what the tool returned: an error message or the text the model reads
	 */
	static String callTool(Skills skills, String toolName, String inputJson) {
		Tool tool = skills.tools()
			.stream()
			.filter((candidate) -> candidate.getDefinition().getName().equals(toolName))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException(
					"No tool named '" + toolName + "'; tools are " + toolNames(skills)));
		Tool.Result result = tool.call(inputJson);
		if (result instanceof Tool.Result.Error error) {
			return "[tool error] " + error.getMessage();
		}
		if (result instanceof Tool.Result.Text text) {
			return text.getContent();
		}
		return String.valueOf(result);
	}

}
