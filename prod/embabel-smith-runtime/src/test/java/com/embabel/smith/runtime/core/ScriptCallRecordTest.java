/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.skills.Skills;
import com.embabel.agent.skills.script.NoOpExecutionEngine;
import com.embabel.agent.skills.script.ProcessSkillScriptExecutionEngine;
import com.embabel.agent.skills.script.SkillScriptExecutionEngine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a run can say after a script ran, as opposed to what it could say before one did.
 *
 * <p>
 * <b>Why the recording is a wrapper and not a hook.</b> The platform's observability seam
 * for tool calls is {@code LlmInteraction.toolCallInspectors}, an argument of an object
 * the platform builds for itself when a step calls the model; a Java host embedding the
 * platform never constructs it. Wrapping the engine is the one place a script call is
 * unavoidable, and the {@code SkillScript} handed to {@code execute} already names the
 * skill it came from and the callable it was offered under — which is two of the five
 * things the record has to state, taken from the platform rather than guessed here.
 *
 * <p>
 * <b>The wrapper adds nothing to what runs.</b> The tool list and the answer are the
 * delegate's, so the assertions are about a line appearing in a log and never about a
 * behaviour difference. The default state is the control for that: {@code none} installs
 * the platform's no-op engine unwrapped, so a boot that runs nothing hands out the
 * platform's own object and records nothing, which is the shape the engine-selection test
 * also pins.
 *
 * <p>
 * Runs with no model and no endpoint. Scripts do execute, confined to a temporary folder,
 * and they only echo, print to standard error and exit.
 *
 * @author Ivan Rodriguez
 */
class ScriptCallRecordTest {

	/**
	 * A script call that asks for nothing: no arguments, no standard input, no input
	 * files.
	 */
	private static final String CALL_WITH_NOTHING = "{\"args\":[],\"stdin\":\"\",\"inputFiles\":[]}";

	@TempDir
	Path tmp;

	@Test
	void aCallIsRecordedBySkillCallableEngineStatusAndElapsed() throws IOException {
		ScriptCallLog calls = new ScriptCallLog();
		Tool ok = tool(skillsOf(recording(calls)), "counter_ok");

		assertThat(calls.isEmpty()).as("nothing is recorded until a call happens").isTrue();

		assertThat(ok.call(CALL_WITH_NOTHING)).as("the call is the ordinary tool call a step makes")
			.isInstanceOf(Tool.Result.Text.class);

		assertThat(calls.lines()).as("one call, one line").hasSize(1);
		assertThat(calls.lines().get(0))
			.as("the five things the run is asked to be able to say, in the one line it says them in")
			.contains("skill 'counter'")
			.contains("counter_ok")
			.contains("ProcessSkillScriptExecutionEngine")
			.contains("exit 0")
			.contains("ms");
	}

	@Test
	void aCallTheEngineRefusesIsRecordedAsRefusedRatherThanLeftOut() throws IOException {
		ScriptCallLog calls = new ScriptCallLog();
		Tool ok = tool(skillsOf(recording(calls)), "counter_ok");

		assertThat(ok.call("{\"args\":[],\"stdin\":\"\",\"inputFiles\":[\"/etc/hostname\"]}"))
			.as("an input outside the root is refused, as the platform's own engine refuses it")
			.isInstanceOf(Tool.Result.Error.class);

		assertThat(calls.lines()).as("and the refusal is in the record, not merely in the answer").hasSize(1);
		assertThat(calls.lines().get(0))
			.as("named as a refusal, with the engine's own reason carried through it, and still named by skill and"
					+ " callable")
			.contains("skill 'counter'")
			.contains("counter_ok")
			.contains("denied")
			.contains("Path traversal");
	}

	@Test
	void theWrapperOffersTheSameToolsAndAnswersTheSameAsTheEngineItWraps() throws IOException {
		ScriptCallLog calls = new ScriptCallLog();
		Skills wrapped = skillsOf(recording(calls));
		Skills plain = skillsOf(ProcessSkillScriptExecutionEngine.confinedTo(this.tmp.toString()));

		assertThat(names(wrapped)).as("the tool list is the delegate's").containsExactlyElementsOf(names(plain));
		assertThat(tool(wrapped, "counter_ok").call(CALL_WITH_NOTHING).getClass()).as("and so is the answer")
			.isEqualTo(tool(plain, "counter_ok").call(CALL_WITH_NOTHING).getClass());
	}

	@Test
	void theDefaultStateRecordsNothingBecauseNothingWasCallable() {
		ScriptCallLog calls = new ScriptCallLog();
		SkillScriptExecutionEngine none = ScriptEngineConfig.recorded(NoOpExecutionEngine.INSTANCE, calls);

		assertThat(none).as("the default boot hands out the platform's own engine, unwrapped")
			.isSameAs(NoOpExecutionEngine.INSTANCE);
		PackSkills carried = PackSkills.at(scriptedPack(), "exec", none);

		assertThat(names((Skills) carried.carried().values().iterator().next()))
			.as("so there is no script call for a log to hold")
			.containsExactlyInAnyOrder("activate", "listResources", "readResource");
		assertThat(calls.isEmpty()).isTrue();
		assertThat(carried.notes())
			.as("while the run still says scripts are not executing, naming the switch that decided it")
			.anyMatch((line) -> line.contains("skill scripts") && line.contains("not executed")
					&& line.contains(PackSkills.SCRIPT_ENGINE_PROPERTY));
	}

	@Test
	void theStateLineNamesTheSkillsWhoseScriptsAreOnTheList() throws IOException {
		PackSkills carried = PackSkills.at(scriptedPack(), "exec", recording(new ScriptCallLog()));

		assertThat(carried.notes())
			.as("the statement that scripts are in play names which skills put them there, so a later call line"
					+ " has something to match")
			.anyMatch((line) -> line.contains("skill scripts") && line.contains("(counter)")
					&& line.contains("offered as tools"));
	}

	/**
	 * The engine the switch selects for {@code process}, with the recording the boot puts
	 * around it.
	 * @param calls the log to write
	 * @return the engine a run would be handed
	 */
	private SkillScriptExecutionEngine recording(ScriptCallLog calls) {
		return new RecordingScriptExecutionEngine(ProcessSkillScriptExecutionEngine.confinedTo(this.tmp.toString()),
				calls);
	}

	/**
	 * Read the staged pack the way a run reads it.
	 * @param engine the engine its steps' scripts run in
	 * @return the skills reference its steps would be given
	 */
	private Skills skillsOf(SkillScriptExecutionEngine engine) {
		PackSkills carried = PackSkills.at(scriptedPack(), "exec", engine);
		return (Skills) carried.carried().values().iterator().next();
	}

	/**
	 * One definition whose {@code skills/} holds one skill with one script that answers.
	 * @return the pack directory, with no shared folder above it
	 */
	private Path scriptedPack() {
		Path skill = this.tmp.resolve("pack").resolve("skills").resolve("counter");
		Path scripts = skill.resolve("scripts");
		try {
			Files.createDirectories(scripts);
			Files.writeString(skill.resolve("SKILL.md"), "---\nname: counter\ndescription: Counts things\n---\n\n"
					+ "Run the counter over the paths you were given.\n");
			Path script = scripts.resolve("ok.sh");
			Files.writeString(script, "#!/bin/bash\necho fine\n");
			script.toFile().setExecutable(true);
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
		return this.tmp.resolve("pack");
	}

	/**
	 * The tool offered under a given callable name.
	 * @param skills the reference a step is given
	 * @param name the callable name to look for
	 * @return the tool
	 */
	private static Tool tool(Skills skills, String name) {
		return skills.tools()
			.stream()
			.filter((candidate) -> candidate.getDefinition().getName().equals(name))
			.findFirst()
			.orElseThrow(() -> new AssertionError("no tool " + name + " among " + names(skills)));
	}

	/**
	 * The callable names a reference offers.
	 * @param skills the reference
	 * @return its tool names
	 */
	private static List<String> names(Skills skills) {
		return skills.tools().stream().map((tool) -> tool.getDefinition().getName()).toList();
	}

}
