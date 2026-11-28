/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.skills.Skills;
import com.embabel.agent.skills.script.NoOpExecutionEngine;
import com.embabel.agent.skills.script.ProcessSkillScriptExecutionEngine;
import com.embabel.agent.skills.script.SkillScriptExecutionEngine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The environment a skill script actually sees, measured by running one that prints its
 * own variable names, and the sentence the run says about it.
 *
 * <p>
 * <b>The claim, stated plainly.</b> The platform's process engine ships an environment
 * allowlist — {@code SAFE_ENV_ALLOWLIST}, fifteen entries like {@code PATH} and
 * {@code HOME} — and applies it <em>only</em> to an engine constructed with host
 * inheritance switched off. That switch is a constructor parameter, and on this platform
 * version no form of that constructor that carries it is visible to a Java caller: every
 * multi-argument one is marked synthetic, so what Java can reach is the no-argument
 * engine and {@code confinedTo(root)}, both of which take the platform default —
 * inheritance on. So the engine the {@code process} selection installs hands a script the
 * whole host environment, API keys among it, and {@code confinedTo} confines
 * <em>paths</em> only. The first case measures that; it is not a claim about a boundary,
 * it is the reason the second case exists.
 *
 * <p>
 * <b>What the run says.</b> A run that offers scripts also says what they can read, on
 * the same line that says scripts are in play, naming the allowlist that this engine does
 * not apply — so an operator does not read "scripts offered as tools" as "scripts
 * sandboxed". With the switch off nothing runs, so that clause is absent rather than
 * negated, and the line still distinguishes a withheld capability from an absent one.
 *
 * <p>
 * Runs with no model and no endpoint. The script prints names of variables and nothing
 * else; it writes no files and reads none.
 *
 * @author Ivan Rodriguez
 */
class ScriptEnvironmentTest {

	/**
	 * A script call that asks for nothing: no arguments, no standard input, no input
	 * files.
	 */
	private static final String CALL_WITH_NOTHING = "{\"args\":[],\"stdin\":\"\",\"inputFiles\":[]}";

	/**
	 * A host that can run no container, which is irrelevant here and only needed because
	 * the selection is asked of the production mapping rather than of an engine built by
	 * hand.
	 */
	private static final ScriptEngineConfig.ContainerAvailability NO_CONTAINERS = new ScriptEngineConfig.ContainerAvailability() {

		@Override
		public boolean dockerAvailable() {
			return false;
		}

		@Override
		public boolean podmanAvailable() {
			return false;
		}

	};

	@TempDir
	Path tmp;

	@Test
	void aScriptSeesTheHostEnvironmentInFullBecauseTheSwitchThatWouldNarrowItIsNotReachableFromJava()
			throws IOException {
		Set<String> outsideTheSafeList = new TreeSet<>(System.getenv().keySet());
		outsideTheSafeList.removeAll(ProcessSkillScriptExecutionEngine.Companion.getSAFE_ENV_ALLOWLIST());
		assertThat(outsideTheSafeList)
			.as("this JVM holds variables the engine's allowlist would have dropped, so the claim below is not vacuous")
			.isNotEmpty();

		List<String> seen = environmentSeenBy(tool(skillsOf(scriptedPack(), engine()), "envprint_env"));

		assertThat(seen).as("the script was given an environment, not an empty one").contains("PATH").isNotEmpty();
		assertThat(seen)
			.as("and every variable this JVM holds beyond the allowlist is in it, because inheritance"
					+ " is on and this platform gives a Java caller no way to switch it off")
			.containsAll(outsideTheSafeList);
	}

	@Test
	void theRunThatOffersScriptsSaysWhatTheyCanBeRead() throws IOException {
		PackSkills carried = PackSkills.at(scriptedPack(), "exec", engine());

		assertThat(carried.notes()).as("the line that says scripts run also names the list it did not apply")
			.anyMatch((line) -> line.contains("script environment") && line.contains("the host's in full")
					&& line.contains("PATH"));
	}

	@Test
	void aRunWithNoEngineSaysNothingAboutAnEnvironmentNothingReads() throws IOException {
		PackSkills carried = PackSkills.at(scriptedPack(), "exec", NoOpExecutionEngine.INSTANCE);

		assertThat(carried.notes()).as("the script state is still stated, withheld rather than absent")
			.anyMatch((line) -> line.contains("skill scripts") && line.contains("not executed"));
		assertThat(carried.notes()).as("and the environment is not, because no script runs to see it")
			.noneMatch((line) -> line.contains("script environment"));
	}

	/**
	 * The engine the {@code process} selection installs, asked of the production mapping
	 * rather than assumed.
	 * @return the engine a run with that switch set would carry
	 */
	private SkillScriptExecutionEngine engine() {
		return ScriptEngineConfig.choose(ScriptEngineConfig.PROCESS, this.tmp.toString(), NO_CONTAINERS);
	}

	/**
	 * The variable names one script call printed.
	 * @param tool the callable that prints them
	 * @return the names it printed, in the order it sorted them
	 */
	private static List<String> environmentSeenBy(Tool tool) {
		Tool.Result answer = tool.call(CALL_WITH_NOTHING);
		assertThat(answer).as("printing an environment answers rather than failing the step")
			.isInstanceOf(Tool.Result.Text.class);
		String content = ((Tool.Result.Text) answer).getContent();
		assertThat(content).as("and the script ran").contains("exit code 0");
		return Arrays.stream(content.split("\\R"))
			.map(String::trim)
			.filter((line) -> line.matches("[A-Za-z_][A-Za-z0-9_]*"))
			.toList();
	}

	/**
	 * Read the staged pack the way a run reads it, with the engine that run would have
	 * selected.
	 * @param pack the definition's directory
	 * @param scriptEngine the engine to hand it
	 * @return the skills reference its steps would be given
	 */
	private static Skills skillsOf(Path pack, SkillScriptExecutionEngine scriptEngine) {
		PackSkills carried = PackSkills.at(pack, "exec", scriptEngine);
		return (Skills) carried.carried().values().iterator().next();
	}

	/**
	 * One definition whose {@code skills/} holds one skill with one script that prints
	 * the names of the variables it was given.
	 * @return the pack directory, with no shared folder above it
	 * @throws IOException if the fixture cannot be written
	 */
	private Path scriptedPack() throws IOException {
		Path skill = this.tmp.resolve("pack").resolve("skills").resolve("envprint");
		Path scripts = skill.resolve("scripts");
		Files.createDirectories(scripts);
		Files.writeString(skill.resolve("SKILL.md"), "---\nname: envprint\ndescription: Prints its environment\n---\n\n"
				+ "Call the script and quote what it printed.\n");
		Path file = scripts.resolve("env.sh");
		Files.writeString(file, "#!/bin/bash\nenv | sed 's/=.*//' | sort\n");
		file.toFile().setExecutable(true);
		return this.tmp.resolve("pack");
	}

	/**
	 * The tool offered under a given callable name.
	 * @param skills the reference a step is given
	 * @param name the callable name to look for
	 * @return the tool
	 */
	private static Tool tool(Skills skills, String name) {
		Optional<Tool> found = skills.tools()
			.stream()
			.filter((candidate) -> candidate.getDefinition().getName().equals(name))
			.findFirst();
		assertThat(found).as("offered tools were " + toolNames(skills)).isPresent();
		return found.get();
	}

	private static List<String> toolNames(Skills skills) {
		return skills.tools().stream().map((tool) -> tool.getDefinition().getName()).toList();
	}

}
