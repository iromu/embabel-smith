/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.skills.Skills;
import com.embabel.agent.skills.script.NoOpExecutionEngine;
import com.embabel.agent.skills.script.ProcessSkillScriptExecutionEngine;
import com.embabel.agent.skills.script.ScriptExecutionResult;
import com.embabel.agent.skills.script.ScriptLanguage;
import com.embabel.agent.skills.script.SkillScript;
import com.embabel.agent.skills.script.SkillScriptExecutionEngine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A script the selected engine will not run is refused out loud, before its absence from
 * the tool list can be read as a capability the run never asked for.
 *
 * <p>
 * <b>What the platform does on its own, and what it does not.</b> {@code Skills.tools()}
 * asks one question of each carried script — is its language among the engine's
 * {@code supportedLanguages()} — and drops it in silence when that fails. The engine's
 * {@code validate} hook, which is the one that explains itself, runs only when a script
 * is executed, by which time there was no tool to execute. And a script whose extension
 * is not a language at all is never even asked: the platform's own table of extensions is
 * the only test it gets, and it matches nothing. So both kinds of refusal reach the
 * report through this module — {@link PackSkills} asks the engine through
 * {@code validate} so the reason is the engine's, and names the extension itself for the
 * file the table knows nothing about.
 *
 * <p>
 * <b>Why the default state says nothing.</b> With no engine selected nothing runs at all,
 * which the script-state line already states; listing every script in the pack as refused
 * would read as a pile of denials for a run that was never offered any. The third case is
 * the control for that silence: it must stay silent about refusals and still say scripts
 * are not executing.
 *
 * <p>
 * Runs with no model and no endpoint, and no script is ever executed — the engine built
 * for this refuses to, so a test that tried would fail rather than pass quietly.
 *
 * @author Ivan Rodriguez
 */
class ScriptRefusalTest {

	@TempDir
	Path tmp;

	@Test
	void anEngineThatDoesNotTakeTheLanguageRefusesTheScriptByFileSkillAndLanguage() throws IOException {
		Path pack = packWith("list.sh", "plot.py");
		PackSkills carried = PackSkills.at(pack, "gate", new BashOnly());

		assertThat(names(carried)).as("the script the engine does take is offered as a tool").contains("gatework_list");
		assertThat(names(carried)).as("and the one it does not take is not").doesNotContain("gatework_plot");
		assertThat(carried.notes())
			.as("the refusal is a line of the report, naming the file, the skill and the language it is in")
			.anyMatch((line) -> line.contains("plot.py") && line.contains("gatework") && line.contains("PYTHON")
					&& line.contains("offered as no tool"));
	}

	@Test
	void aScriptInTheTableKnowsNothingAboutIsNamedWithItsExtension() throws IOException {
		Path pack = packWith("list.sh", "notes.ps1");
		PackSkills carried = PackSkills.at(pack, "gate", ProcessSkillScriptExecutionEngine.confinedTo(pack.toString()));

		assertThat(names(carried)).as("the recognised script is offered by the real engine").contains("gatework_list");
		assertThat(names(carried)).as("and the one with no language at all is not").doesNotContain("gatework_notes");
		assertThat(carried.notes()).as("named by file and by the extension nothing maps, because no hook ever saw it")
			.anyMatch((line) -> line.contains("notes.ps1") && line.contains("ps1")
					&& line.contains("no language the platform knows"));
	}

	@Test
	void theDefaultStateNamesNoRefusalsBecauseItOfferedNothingToRefuse() throws IOException {
		Path pack = packWith("list.sh", "plot.py", "notes.ps1");
		PackSkills carried = PackSkills.at(pack, "gate", NoOpExecutionEngine.INSTANCE);

		assertThat(names(carried)).as("no engine, so no script tool of any kind")
			.containsExactlyInAnyOrder("activate", "listResources", "readResource");
		assertThat(carried.notes()).as("and no refusal is claimed")
			.noneMatch((line) -> line.contains("offered as no tool"));
		assertThat(carried.notes()).as("while the run still says scripts are not executing, naming the switch")
			.anyMatch((line) -> line.contains("skill scripts") && line.contains("not executed")
					&& line.contains(PackSkills.SCRIPT_ENGINE_PROPERTY));
	}

	/**
	 * The callable names one definition's skills are offered under.
	 * @param carried what the run read from the pack
	 * @return the tool names
	 */
	private static List<String> names(PackSkills carried) {
		Skills skills = (Skills) carried.carried().values().iterator().next();
		return skills.tools().stream().map(Tool::getDefinition).map(Tool.Definition::getName).toList();
	}

	/**
	 * One definition whose {@code skills/} holds one skill carrying the given scripts.
	 * @param files the script file names to write
	 * @return the pack directory, with no shared folder above it
	 * @throws IOException if the fixture cannot be written
	 */
	private Path packWith(String... files) throws IOException {
		Path skill = this.tmp.resolve("pack").resolve("skills").resolve("gatework");
		Path scripts = skill.resolve("scripts");
		Files.createDirectories(scripts);
		Files.writeString(skill.resolve("SKILL.md"), "---\nname: gatework\ndescription: Carries scripts\n---\n\n"
				+ "One of these runs; the others are here to be refused.\n");
		for (String file : files) {
			Files.writeString(scripts.resolve(file), "echo 1\n");
		}
		return this.tmp.resolve("pack");
	}

	/**
	 * An engine that takes bash and nothing else, which is the case the platform's
	 * language test refuses without ever consulting a reason. Its {@code validate} is the
	 * interface's own, so the sentence the report carries is the platform's wording and
	 * not a string authored here.
	 */
	private static final class BashOnly implements SkillScriptExecutionEngine {

		@Override
		public Set<ScriptLanguage> supportedLanguages() {
			return Set.of(ScriptLanguage.BASH);
		}

		@Override
		public ScriptExecutionResult execute(SkillScript script, List<String> args, String stdin,
				List<? extends Path> inputFiles) {
			throw new UnsupportedOperationException("this test asks what is offered, never what running would do");
		}

	}

}
