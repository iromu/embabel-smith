/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.core;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.skills.Skills;
import com.embabel.agent.skills.script.ProcessSkillScriptExecutionEngine;
import com.embabel.agent.skills.script.ScriptExecutionResult;
import com.embabel.agent.skills.script.SkillScriptExecutionEngine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a script that prints without bound hands to the model, which is the one script
 * outcome the platform does not decide.
 *
 * <p>
 * <b>Why the ceiling is measured rather than assumed.</b> Measured against this platform
 * version, a script's whole standard output is returned: {@code ScriptTool} appends the
 * captured streams as they came and the IO pump caps nothing, so the spec's "truncated
 * with the truncation stated" is not a behaviour to observe but one to author. The
 * ceiling therefore lives in {@link RecordingScriptExecutionEngine}, which is the last
 * place the output exists as a single value, and what these tests pin is the three things
 * a ceiling can get wrong: it cuts without saying so, it says so without cutting, or it
 * is quietly not in force at all.
 *
 * <p>
 * <b>Why the rewrite route gets its own test.</b> A {@code Success} cannot be constructed
 * by a Java caller — its usable constructors carry a Kotlin marker — but the value
 * class's {@code copy} method is public, so the engine resolves it by shape rather than
 * by the mangled spelling. That shape is asserted here against the pinned jar,
 * independently of the code that uses it, so a platform release that renames the method
 * fails a test at build time instead of leaving every run uncapped with a passing suite.
 * The other test covers the branch that resolution failure takes: the script's full
 * output still reaches the model, and the run says the ceiling was not applied.
 *
 * <p>
 * Runs with no model and no endpoint. Scripts do execute, confined to a temporary folder,
 * and they only print.
 *
 * @author Ivan Rodriguez
 */
class ScriptOutputCapTest {

	/**
	 * A script call that asks for nothing.
	 */
	private static final String CALL_WITH_NOTHING = "{\"args\":[],\"stdin\":\"\",\"inputFiles\":[]}";

	@TempDir
	Path tmp;

	@Test
	void aRunawayPrinterIsCutDownAndTheCutIsStated() throws IOException {
		Tool big = tool(skillsOf(new RecordingScriptExecutionEngine(engine(), new ScriptCallLog(), 200)),
				"counter_big");

		Tool.Result answer = big.call(CALL_WITH_NOTHING);

		assertThat(answer).as("a printer that runs on is still an answer the step survives")
			.isInstanceOf(Tool.Result.Text.class);
		String given = ((Tool.Result.Text) answer).getContent();
		assertThat(given).as("the beginning of what it printed is there").contains("headmarker");
		assertThat(given).as("the part past the ceiling is not").doesNotContain("tailmarker");
		assertThat(given).as("and the text says that a cut happened, in the output itself")
			.contains("output truncated")
			.contains("characters printed");
	}

	@Test
	void outputUnderTheCeilingPassesWholeAndSaysNothingAboutATruncation() throws IOException {
		Tool small = tool(skillsOf(new RecordingScriptExecutionEngine(engine(), new ScriptCallLog(),
				RecordingScriptExecutionEngine.DEFAULT_OUTPUT_CAP)), "counter_small");

		String given = ((Tool.Result.Text) small.call(CALL_WITH_NOTHING)).getContent();

		assertThat(given).as("the whole of it, both ends").contains("headmarker").contains("tailmarker");
		assertThat(given).as("and no sentence about a cut, because there was none").doesNotContain("truncated");
	}

	@Test
	void theRewriteRouteTheCeilingNeedsIsThereInThePlatformItWasWrittenAgainst() {
		List<Method> shapes = List.of(ScriptExecutionResult.Success.class.getDeclaredMethods())
			.stream()
			.filter((candidate) -> candidate.getReturnType().equals(ScriptExecutionResult.Success.class))
			.filter((candidate) -> java.util.Arrays.equals(candidate.getParameterTypes(),
					new Class<?>[] { String.class, String.class, int.class, long.class, List.class }))
			.toList();

		assertThat(shapes)
			.as("a public copy taking (stdout, stderr, exit, duration, artifacts) is what lets a"
					+ " ceiling be applied at all; if this goes red the platform renamed it and every run is"
					+ " silently uncapped")
			.hasSize(1);
	}

	@Test
	void aCeilingThatCannotBeAppliedHandsOverTheWholeOutputAndSaysSo() throws IOException {
		ScriptCallLog calls = new ScriptCallLog();
		RecordingScriptExecutionEngine unwritable = new RecordingScriptExecutionEngine(engine(), calls, 200,
				(succeeded, cap) -> null);

		String given = ((Tool.Result.Text) tool(skillsOf(unwritable), "counter_big").call(CALL_WITH_NOTHING))
			.getContent();

		assertThat(given).as("the call still answers, with everything in it")
			.contains("headmarker")
			.contains("tailmarker");
		assertThat(given).as("and the text is not made to claim a cut that did not happen").doesNotContain("truncated");
		assertThat(calls.lines()).as("the run says the ceiling was not applied, rather than promising it silently")
			.anyMatch((line) -> line.contains("ceiling was not applied") && line.contains("characters"));
	}

	/**
	 * The engine the switch would have selected for this run.
	 * @return a process engine confined to the temporary root
	 */
	private SkillScriptExecutionEngine engine() {
		return ProcessSkillScriptExecutionEngine.confinedTo(this.tmp.toString());
	}

	/**
	 * Read the staged pack the way a run reads it, with the given engine.
	 * @param engine the engine the boot hands out
	 * @return the skills reference its steps would be given
	 * @throws IOException if the fixture cannot be written
	 */
	private Skills skillsOf(SkillScriptExecutionEngine engine) throws IOException {
		packWithScripts();
		PackSkills carried = PackSkills.at(this.tmp.resolve("pack"), "cap", engine);
		return (Skills) carried.carried().values().iterator().next();
	}

	/**
	 * The tool offered under a callable name, with the offered names in the failure
	 * message so a wrong guess says what was there.
	 * @param skills the reference a step is given
	 * @param name the callable name to look for
	 * @return the tool
	 */
	private static Tool tool(Skills skills, String name) {
		Optional<Tool> found = skills.tools()
			.stream()
			.filter((candidate) -> candidate.getDefinition().getName().equals(name))
			.findFirst();
		assertThat(found)
			.as("offered tools were " + skills.tools().stream().map((tool) -> tool.getDefinition().getName()).toList())
			.isPresent();
		return found.get();
	}

	/**
	 * A definition whose one skill carries two scripts: one that prints past any ceiling
	 * and one that does not, so the second is the control that makes the first mean
	 * something.
	 * @throws IOException if the fixture cannot be written
	 */
	private void packWithScripts() throws IOException {
		Path skill = this.tmp.resolve("pack").resolve("skills").resolve("counter");
		Path scripts = skill.resolve("scripts");
		Files.createDirectories(scripts);
		Files.writeString(skill.resolve("SKILL.md"), "---\nname: counter\ndescription: Prints things\n---\n\n"
				+ "Run a script and quote what it printed.\n");
		StringBuilder run_on = new StringBuilder("#!/bin/bash\necho headmarker\n");
		for (int line = 0; line < 600; line++) {
			run_on.append("printf 'padding padding padding padding padding\\n'\n");
		}
		run_on.append("echo tailmarker\n");
		script(scripts, "big.sh", run_on.toString());
		script(scripts, "small.sh", "#!/bin/bash\necho headmarker\necho tailmarker\n");
		Files.createDirectories(this.tmp.resolve("pack/steps"));
		Files.writeString(this.tmp.resolve("pack/manifest.yml"), "name: cap\nversion: 0.1.0\n");
	}

	/**
	 * Write one script into the fixture.
	 * @param scripts the skill's {@code scripts/} directory
	 * @param name the file name the callable is built from
	 * @param body the whole file contents
	 * @throws IOException if the file cannot be written
	 */
	private static void script(Path scripts, String name, String body) throws IOException {
		Path file = scripts.resolve(name);
		Files.writeString(file, body);
		file.toFile().setExecutable(true);
	}

}
