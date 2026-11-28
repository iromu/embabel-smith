/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.core;

import java.io.IOException;
import java.lang.reflect.Field;
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
 * <b>The pair, in the order that makes each mean something.</b> The platform's process
 * engine ships an environment allowlist — {@code SAFE_ENV_ALLOWLIST}, fifteen entries
 * like {@code PATH} and {@code HOME} — and applies it <em>only</em> to an engine
 * constructed with host inheritance switched off, which its own factories never do:
 * {@code confinedTo(root)} passes a root and nothing else. So the platform default hands
 * a script the whole host environment, API keys among it, and that is what the second
 * case measures. The first case measures the other half of the story, which is that this
 * module does not ship that engine: the constructors Java can name stop before the pair
 * wanted here — the public forms that carry the inheritance switch take no confined root,
 * and the one that takes both is private — so {@code ScriptEngineConfig} names that
 * constructor to the JVM and builds an engine that withholds the host environment, and a
 * variable outside the allowlist does not reach the child. The two cases are the same
 * script run twice against the same host — one assertion each side of a real difference,
 * not a claim about a boundary nobody can observe.
 *
 * <p>
 * <b>What the run says.</b> A run that offers scripts also says what they can read, on
 * the line that says scripts are in play, so an operator does not read "scripts offered
 * as tools" as "scripts sandboxed" — or, now that the list is applied, read the reverse
 * of it. With the switch off nothing runs, so that clause is absent rather than negated,
 * and the line still distinguishes a withheld capability from an absent one.
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
	 * The two names the engine writes into every script's environment itself, after any
	 * filtering. They are excluded from the host-variable comparison because they are not
	 * host variables, and a shell that happened to export one would otherwise turn the
	 * first case below into a coin flip.
	 */
	private static final List<String> RUN_DIRECTORIES = List.of("INPUT_DIR", "OUTPUT_DIR");

	/**
	 * Names the interpreter puts into its own environment as it starts, whether or not
	 * anything handed them over: the last command's path, the working directory, the
	 * shell nesting depth. They are excluded from the comparison because they arrive
	 * after the engine's filter and would otherwise be indistinguishable from a leak —
	 * measured rather than assumed: the isolated child's entire environment is the
	 * allowlist plus {@link #RUN_DIRECTORIES} plus exactly these, and the case that names
	 * the platform default hands over hundreds more.
	 */
	private static final Set<String> INTERPRETER_OWNED = Set.of("_", "PWD", "SHLVL", "OLDPWD");

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
	void theEngineThisModuleInstallsKeepsHostVariablesOutOfTheScript() throws IOException {
		Set<String> outsideTheSafeList = hostVariablesOutsideTheAllowlist();
		assertThat(outsideTheSafeList)
			.as("this JVM holds variables the allowlist would drop, so the claim below is not vacuous")
			.isNotEmpty();

		List<String> seen = environmentSeenBy(tool(skillsOf(scriptedPack(), engine()), "envprint_env"));

		assertThat(seen)
			.as("the script was given a working environment, not an emptied one — an interpreter"
					+ " still has to be findable")
			.contains("PATH");
		Set<String> whatMayArrive = new TreeSet<>(ProcessSkillScriptExecutionEngine.Companion.getSAFE_ENV_ALLOWLIST());
		whatMayArrive.addAll(RUN_DIRECTORIES);
		whatMayArrive.addAll(INTERPRETER_OWNED);
		assertThat(seen)
			.as("and the child's whole environment is that list, the two run directories and what"
					+ " the interpreter writes itself — nothing else")
			.isSubsetOf(whatMayArrive);
		assertThat(seen).as("stated as a refusal: none of this JVM's own variables is among them")
			.doesNotContainAnyElementsOf(outsideTheSafeList);
	}

	@Test
	void thePlatformsOwnDefaultEngineLeaksThoseSameVariables() throws IOException {
		Set<String> outsideTheSafeList = hostVariablesOutsideTheAllowlist();

		List<String> seen = environmentSeenBy(
				tool(skillsOf(scriptedPack(), ProcessSkillScriptExecutionEngine.confinedTo(this.tmp.toString())),
						"envprint_env"));

		assertThat(seen)
			.as("the engine the platform's own factory builds hands over every variable this JVM holds beyond"
					+ " the allowlist, which is the reason the case above exists rather than a rhetorical one")
			.containsAll(outsideTheSafeList);
	}

	@Test
	void theRunDirectoriesReachTheScriptAfterTheFilter() throws IOException {
		List<String> seen = environmentSeenBy(tool(skillsOf(scriptedPack(), engine()), "envprint_env"));

		assertThat(seen)
			.as("the engine writes these itself, after filtering, so withholding the host environment does not"
					+ " leave a script unable to find the folder it was given")
			.containsAll(RUN_DIRECTORIES);
	}

	@Test
	void theEngineBuiltWithInheritanceOffStillCarriesThePlatformsOwnTimeout() throws ReflectiveOperationException {
		Field timeout = ProcessSkillScriptExecutionEngine.class.getDeclaredField("timeout");
		timeout.setAccessible(true);

		assertThat(timeout.getLong(ProcessSkillScriptExecutionEngine.confinedTo(this.tmp.toString())))
			.as("the constructor takes the timeout as a raw value-class long, and this module passes the"
					+ " platform's own default rather than a number of its own; if this goes red the platform"
					+ " changed its default and every run's limit changed with it")
			.isEqualTo(ScriptEngineConfig.PLATFORM_DEFAULT_TIMEOUT);
	}

	@Test
	void theRunThatOffersScriptsSaysWhatTheyCanBeRead() throws IOException {
		PackSkills carried = PackSkills.at(scriptedPack(), "exec", engine());

		assertThat(carried.notes())
			.as("the line that says scripts run also names the list, so the sentence cannot be read as"
					+ " silence about the environment")
			.anyMatch((line) -> line.contains("script environment") && line.contains("PATH"));
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
	 * The variables this JVM holds that the engine's allowlist does not name, minus the
	 * two the engine adds to every script itself.
	 * @return the names, sorted, so a failure prints them legibly
	 */
	private static Set<String> hostVariablesOutsideTheAllowlist() {
		Set<String> outside = new TreeSet<>(System.getenv().keySet());
		outside.removeAll(ProcessSkillScriptExecutionEngine.Companion.getSAFE_ENV_ALLOWLIST());
		outside.removeAll(RUN_DIRECTORIES);
		outside.removeAll(INTERPRETER_OWNED);
		return outside;
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
