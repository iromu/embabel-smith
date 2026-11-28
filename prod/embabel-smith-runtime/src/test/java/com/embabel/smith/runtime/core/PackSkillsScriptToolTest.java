/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.core;

import com.embabel.agent.skills.Skills;
import com.embabel.agent.skills.script.ProcessSkillScriptExecutionEngine;
import com.embabel.agent.skills.support.LoadedSkill;
import com.embabel.agent.api.tool.Tool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The claim box 6.1 exists to keep: adding {@code embabel-agent-skills} to the runtime is
 * not what starts offering script tools. The dependency only puts the classes on the
 * classpath; {@link Skills} assembles with the platform's {@code NoOpExecutionEngine}
 * unless something hands it another engine, and this module hands it nothing until
 * {@code smith.tools.scripts.engine} says so. So a pack whose skill ships a script is
 * loaded, is known to carry that script, and still gets no tool that could run it.
 *
 * <p>
 * <b>Why the second test is here rather than a comment.</b> An absence assertion on its
 * own is satisfied by the fixture being wrong — a skill that never loaded, a script the
 * loader dropped, a naming scheme nobody guessed. So the control leg takes the very same
 * reference, attaches the platform's own process engine to it, and asks again: the script
 * tool appears. The difference between the two lists is therefore the engine and nothing
 * else, which is the quantity this box is about.
 *
 * <p>
 * Runs with no model and no endpoint. The engine is constructed but never invoked — the
 * test asks what is offered, never what running a script would do.
 *
 * @author Ivan Rodriguez
 */
class PackSkillsScriptToolTest {

	/** The three tools a skill reference offers without any script engine. */
	private static final Set<String> WITHOUT_SCRIPT_TOOLS = Set.of("activate", "listResources", "readResource");

	@TempDir
	Path tmp;

	@Test
	void aScriptBearingSkillIsLoadedAndCarriesItsScript() throws IOException {
		Skills skills = skillsOf(scriptBearingPack());

		assertThat(skills.getSkills()).as("the fixture really loaded")
			.extracting(LoadedSkill::getName)
			.containsExactly("counter");
		assertThat(skills.getSkills().get(0).hasScripts()).as("and the loader really saw the script in it").isTrue();
	}

	@Test
	void nothingConfiguredOffersNoScriptTool() throws IOException {
		Skills skills = skillsOf(scriptBearingPack());

		assertThat(names(skills)).as("a skill folder on the classpath is not a script tool being offered")
			.containsExactlyInAnyOrderElementsOf(WITHOUT_SCRIPT_TOOLS);
	}

	@Test
	void theSameReferenceOffersTheScriptToolOnceAnEngineIsAttached() throws IOException {
		Path pack = scriptBearingPack();
		Skills skills = skillsOf(pack)
			.withScriptExecutionEngine(ProcessSkillScriptExecutionEngine.confinedTo(pack.toString()));

		assertThat(names(skills)).as("the same reference, with the platform's own engine, offers the script")
			.contains("counter_count");
		assertThat(names(skills)).containsAll(WITHOUT_SCRIPT_TOOLS);
	}

	@Test
	void anEngineHandedToTheLoaderIsTheEngineTheStepsGet() throws IOException {
		Path pack = scriptBearingPack();
		PackSkills carried = PackSkills.at(pack, "scripttool",
				ProcessSkillScriptExecutionEngine.confinedTo(pack.toString()));
		Skills skills = (Skills) carried.carried().values().iterator().next();

		assertThat(names(skills))
			.as("the route a run takes: an engine arriving as an argument, not attached afterwards")
			.contains("counter_count");
		assertThat(carried.notes()).as("and the report says scripts are offered, naming what decided it")
			.anyMatch((line) -> line.contains("skill scripts") && line.contains(PackSkills.SCRIPT_ENGINE_PROPERTY)
					&& !line.contains("not executed"));
	}

	/**
	 * Read one pack's skills the way a run does, and take the container it offers under.
	 * @param pack the definition's directory
	 * @return the skills reference the run would put in front of its steps
	 */
	private Skills skillsOf(Path pack) {
		PackSkills carried = PackSkills.at(pack, "scripttool");
		return (Skills) carried.carried().values().iterator().next();
	}

	/**
	 * A definition whose own {@code skills/} holds one skill with one script.
	 * @return the pack directory
	 * @throws IOException if the fixture cannot be written
	 */
	private Path scriptBearingPack() throws IOException {
		Path skill = tmp.resolve("skills").resolve("counter");
		Files.createDirectories(skill.resolve("scripts"));
		Files.writeString(skill.resolve("SKILL.md"), "---\nname: counter\ndescription: Counts things\n---\n\n"
				+ "Run the counter over the paths you were given.\n");
		Files.writeString(skill.resolve("scripts").resolve("count.sh"), "echo 1\n");
		return tmp;
	}

	/**
	 * The callable names a reference offers.
	 * @param skills the reference
	 * @return its tool names
	 */
	private static List<String> names(Skills skills) {
		return skills.tools().stream().map(Tool::getDefinition).map(Tool.Definition::getName).toList();
	}

}
