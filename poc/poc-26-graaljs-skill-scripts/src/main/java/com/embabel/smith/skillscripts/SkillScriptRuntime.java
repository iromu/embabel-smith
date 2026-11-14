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

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.skills.Skills;
import com.embabel.agent.skills.script.ScriptExecutionResult;
import com.embabel.agent.skills.script.SkillScript;
import com.embabel.agent.skills.script.SkillScriptExecutionEngine;
import com.embabel.agent.skills.support.ClaudeFrontMatterFormatter;
import com.embabel.agent.skills.support.DefaultDirectorySkillDefinitionLoader;
import com.embabel.agent.skills.support.GitHubSkillDefinitionLoader;
import com.embabel.agent.skills.support.LoadedSkill;

/**
 * Loads a folder of Agent Skills the way an Embabel agent does, with a
 * {@link SkillScriptExecutionEngine} installed, and exposes the two things the POC needs to
 * show: the tool list the model would see, and a way to invoke a script tool deterministically
 * (no model in the loop) so the wiring is pinned by offline tests.
 *
 * @author Ivan Rodriguez
 */
public final class SkillScriptRuntime {

	private final SkillScriptExecutionEngine engine;

	public SkillScriptRuntime(SkillScriptExecutionEngine engine) {
		this.engine = engine;
	}

	/**
	 * Load every skill immediately under {@code skillsDir} and bind the engine to them. This is
	 * the one line that turns a skill's {@code scripts/} folder into callable tools.
	 * @param skillsDir directory whose subdirectories are skill folders
	 * @return the skills reference to hand to an agent.
	 */
	public Skills skillsFrom(Path skillsDir) {
		return new Skills("smith-skill-scripts", "Domain skills whose scripts are executed in-process",
				List.of(), new DefaultDirectorySkillDefinitionLoader(true), new GitHubSkillDefinitionLoader(),
				ClaudeFrontMatterFormatter.INSTANCE)
			.withLocalSkills(skillsDir.toString())
			.withScriptExecutionEngine(this.engine);
	}

	/**
	 * The tools the platform would offer the model for these skills.
	 * @param skills the loaded skills
	 * @return the tool names, in the order Embabel exposes them.
	 */
	public List<String> toolNames(Skills skills) {
		return skills.tools().stream().map((tool) -> tool.getDefinition().getName()).toList();
	}

	/**
	 * Find one of the tools these skills expose.
	 * @param skills the loaded skills
	 * @param toolName the tool to find
	 * @return the tool, if the skills expose one with that name.
	 */
	public Optional<Tool> tool(Skills skills, String toolName) {
		return skills.tools()
			.stream()
			.filter((tool) -> tool.getDefinition().getName().equals(toolName))
			.findFirst();
	}

	/**
	 * Invoke a tool through the same entry point the platform uses, so the model's view and the
	 * test's view are the same code path.
	 * @param skills the loaded skills
	 * @param toolName the script tool to call
	 * @param inputJson the tool input, in the {@code {args, stdin, inputFiles}} shape
	 * @return the text the tool returned to the model.
	 */
	public String callTool(Skills skills, String toolName, String inputJson) {
		Tool tool = tool(skills, toolName)
			.orElseThrow(() -> new IllegalArgumentException("No tool named '" + toolName + "'; tools are "
					+ toolNames(skills)));
		Tool.Result result = tool.call(inputJson);
		if (result instanceof Tool.Result.Error error) {
			return "[tool error] " + error.getMessage();
		}
		if (result instanceof Tool.Result.Text text) {
			return text.getContent();
		}
		return String.valueOf(result);
	}

	/**
	 * Run one script of one skill directly against the engine, bypassing the tool wrapper.
	 * @param skills the loaded skills
	 * @param skillName the skill owning the script
	 * @param fileName the script file name, e.g. {@code compute_totals.js}
	 * @param args arguments to hand the script
	 * @param stdin text to hand the script as its input
	 * @param inputFiles files staged for the script's {@code readFile}
	 * @return the engine's result.
	 */
	public ScriptExecutionResult runScript(Skills skills, String skillName, String fileName, List<String> args,
			String stdin, List<Path> inputFiles) {
		LoadedSkill skill = skills.getSkills()
			.stream()
			.filter((candidate) -> candidate.getName().equals(skillName))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("No skill named '" + skillName + "'"));
		SkillScript script = skill.getScripts()
			.stream()
			.filter((candidate) -> candidate.getFileName().equals(fileName))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException(
					"Skill '" + skillName + "' has no script named '" + fileName + "'"));
		return this.engine.execute(script, args, stdin, inputFiles);
	}

}
