/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.core;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.skills.Skills;
import com.embabel.agent.skills.script.ProcessSkillScriptExecutionEngine;
import com.embabel.agent.skills.script.SkillScriptExecutionEngine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a script tool answers when it is called, measured by calling the tool a model
 * would call rather than the engine underneath it.
 *
 * <p>
 * <b>Why the tool and not the engine.</b> {@code Skills.tools()} hands out ordinary
 * {@link Tool} objects whose {@code call(String)} is the same entry point the model's
 * tool call reaches, so everything below is asserted against the surface a step actually
 * sees: the pack is read the way a run reads it
 * ({@link PackSkills#at(Path, String, com.embabel.agent.skills.script.SkillScriptExecutionEngine)}),
 * the engine is the one {@code smith.tools.scripts.engine} selects with {@code process},
 * and the assertions are about the result the step receives.
 *
 * <p>
 * <b>Which outcomes are the platform's, and which are still to be authored.</b> A
 * non-zero exit arrives as a {@code Text} result carrying the status and what the script
 * wrote to standard error — an answer the model can act on rather than a step that
 * failed, which is what the spec asks for and nothing this repo has to author. An input
 * path outside the root arrives as an {@code Error} naming the path it refused and the
 * root it refused against; the root it names is the per-call staging directory the
 * platform stages artifacts into, not the run root the engine was confined to, and the
 * case says so rather than implying a confinement the platform does not claim. One of the
 * spec's outcomes is deliberately not here. A script that prints too much is <i>not</i>
 * truncated: measured against this platform version, megabytes of standard output are
 * returned whole, with no cap and no marker, so truncation-with-the-truncation-stated
 * needs a wrapper of our own. A timeout <i>is</i> here, by shortening the engine's own
 * limit rather than waiting out its default: the limit is a constructor parameter, but
 * the leading {@code Duration} makes the deeper overloads synthetic — the platform says
 * so in its own comment on {@code confinedTo} — so the engine is reached by reflection.
 * What the engine makes of the number it is handed is not asserted, only that it stops
 * well inside the script's own twenty-second sleep; the alternative was a script that
 * sleeps past the thirty-second default, which would add that wait to every run of the
 * suite.
 *
 * <p>
 * <b>The name is aimable as built, and traceable as offered.</b> The callable is
 * {@code <skill>_<file-basename>} formed without sanitization, so a hyphenated script
 * file keeps its hyphen in the name a model is offered — and the sanitized spelling is
 * not offered alongside it, which is the negative that makes the positive mean something.
 * The callable's own description names the file it runs, so the wire name and the file
 * behind it travel together on the surface a step sees, without anything of ours logging
 * the pairing; which file a name came from is therefore recoverable from the tool list
 * alone.
 *
 * <p>
 * Runs with no model and no endpoint. Scripts do execute, confined to a temporary folder,
 * and they only echo, print to standard error, and exit.
 *
 * @author Ivan Rodriguez
 */
class ScriptExecutionTest {

	/**
	 * A script call that asks for nothing: no arguments, no standard input, no input
	 * files.
	 */
	private static final String CALL_WITH_NOTHING = "{\"args\":[],\"stdin\":\"\",\"inputFiles\":[]}";

	@TempDir
	Path tmp;

	@Test
	void aScriptThatExitsNonZeroAnswersRatherThanFailingTheRun() throws IOException {
		Tool fail = tool(skillsOf(scriptedPack()), "counter_fail");

		assertThat(fail.getDefinition().getDescription()).as("the callable says which file it runs")
			.contains("fail.sh");

		Tool.Result answer = fail.call(CALL_WITH_NOTHING);

		assertThat(answer).as("a non-zero exit is an answer, not a failure of the step")
			.isInstanceOf(Tool.Result.Text.class);
		assertThat(((Tool.Result.Text) answer).getContent()).as("and the status is in what the step is given")
			.contains("exit code 3");
		assertThat(((Tool.Result.Text) answer).getContent()).as("and so is what the script wrote to standard error")
			.contains("nothing to show");
	}

	@Test
	void anInputOutsideTheRootIsRefusedNamingThePathAndTheRoot() throws IOException {
		Tool ok = tool(skillsOf(scriptedPack()), "counter_ok");

		Tool.Result refusal = ok.call("{\"args\":[],\"stdin\":\"\",\"inputFiles\":[\"/etc/hostname\"]}");

		assertThat(refusal).isInstanceOf(Tool.Result.Error.class);
		String message = ((Tool.Result.Error) refusal).getMessage();
		assertThat(message).as("the refusal names what it would not read").contains("/etc/hostname");
		assertThat(message).as("and the root it measured that against").contains("root=").contains("skill-artifacts");
	}

	@Test
	void aScriptThatOutlivesTheEnginesLimitIsStoppedAndSaysItTimedOut() throws IOException {
		Path pack = scriptedPack();
		script(this.tmp.resolve("pack/skills/counter/scripts"), "slow.sh", "sleep 20\necho never\n");
		ScriptCallLog calls = new ScriptCallLog();
		Tool slow = tool(
				skillsOf(pack, new RecordingScriptExecutionEngine(engineTimingOutAfter(Duration.ofSeconds(1)), calls)),
				"counter_slow");

		long started = System.nanoTime();
		Tool.Result answer = slow.call(CALL_WITH_NOTHING);
		long secondsRun = Duration.ofNanos(System.nanoTime() - started).toSeconds();

		assertThat(answer).as("a stopped script is an error the step survives, not a step that failed")
			.isInstanceOf(Tool.Result.Error.class);
		assertThat(((Tool.Result.Error) answer).getMessage()).as("and the error says the reason was the limit")
			.contains("timed out");
		assertThat(secondsRun).as("it was stopped rather than run out: the script sleeps twenty seconds")
			.isLessThan(10);
		assertThat(calls.lines()).as("the call record says the same thing, so a timeout is not a plain exit code")
			.singleElement()
			.satisfies((line) -> assertThat(line).contains("counter_slow").contains("timed out"));
	}

	@Test
	void theCallableIsNamedFromTheScriptFileWithoutSanitization() throws IOException {
		Skills skills = skillsOf(scriptedPack());

		assertThat(toolNames(skills)).as("the file name survives into the callable, hyphen and all")
			.contains("counter_count-things", "counter_ok");
		assertThat(toolNames(skills)).as("and the underscored spelling is not offered alongside it")
			.doesNotContain("counter_count_things");
		Tool hyphenated = tool(skills, "counter_count-things");
		assertThat(hyphenated.getDefinition().getDescription())
			.as("the same line says which file it came from, so the wire name stays traceable")
			.contains("count-things.sh");
		assertThat(hyphenated.call(CALL_WITH_NOTHING)).as("so the name a model is offered is the name that answers")
			.isInstanceOf(Tool.Result.Text.class);
	}

	/**
	 * Read the staged pack the way a run reads it, with the process engine confined to
	 * the temporary root.
	 * @param pack the definition's directory
	 * @return the skills reference its steps would be given
	 */
	private Skills skillsOf(Path pack) {
		return skillsOf(pack, ProcessSkillScriptExecutionEngine.confinedTo(this.tmp.toString()));
	}

	/**
	 * Read the staged pack with a given engine, which is how a run reads it.
	 * @param pack the definition's directory
	 * @param engine the engine the boot selected
	 * @return the skills reference its steps would be given
	 */
	private static Skills skillsOf(Path pack, SkillScriptExecutionEngine engine) {
		PackSkills carried = PackSkills.at(pack, "exec", engine);
		return (Skills) carried.carried().values().iterator().next();
	}

	/**
	 * The process engine with a shortened execution limit. The limit is a constructor
	 * parameter, but the leading {@code Duration} value-class argument makes the deeper
	 * overloads synthetic — {@code confinedTo}'s own comment says a Java caller cannot
	 * reach them — so the one-argument form is asked for by reflection. Nothing else is
	 * changed: languages, interpreters and staging stay at their defaults.
	 * @param timeout how long a script may run before the engine kills it
	 * @return an engine that stops scripts after that long
	 */
	private static SkillScriptExecutionEngine engineTimingOutAfter(Duration timeout) {
		try {
			Constructor<?> limit = ProcessSkillScriptExecutionEngine.class.getDeclaredConstructor(long.class);
			limit.setAccessible(true);
			return (ProcessSkillScriptExecutionEngine) limit.newInstance(timeout.toNanos());
		}
		catch (ReflectiveOperationException ex) {
			throw new IllegalStateException("the engine's timeout is no longer reachable the way this test reads "
					+ "it, so the timeout case needs the thirty-second default instead", ex);
		}
	}

	/**
	 * One definition whose {@code skills/} holds one skill with three scripts: one that
	 * answers, one that fails, one with a hyphen in its name.
	 * @return the pack directory, with no shared folder above it
	 * @throws IOException if the fixture cannot be written
	 */
	private Path scriptedPack() throws IOException {
		Path skill = this.tmp.resolve("pack").resolve("skills").resolve("counter");
		Path scripts = skill.resolve("scripts");
		Files.createDirectories(scripts);
		Files.writeString(skill.resolve("SKILL.md"), "---\nname: counter\ndescription: Counts things\n---\n\n"
				+ "Run a script over the paths you were given and quote what it printed.\n");
		script(scripts, "ok.sh", "echo fine\n");
		script(scripts, "fail.sh", "echo nothing to show >&2\nexit 3\n");
		script(scripts, "count-things.sh", "echo counted\n");
		return this.tmp.resolve("pack");
	}

	/**
	 * Write one script into the fixture.
	 * @param scripts the skill's {@code scripts/} directory
	 * @param name the file name, which is what the callable is built from
	 * @param body the commands
	 * @throws IOException if the file cannot be written
	 */
	private static void script(Path scripts, String name, String body) throws IOException {
		Path file = scripts.resolve(name);
		Files.writeString(file, "#!/bin/bash\n" + body);
		file.toFile().setExecutable(true);
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

	/**
	 * The callable names a reference offers.
	 * @param skills the reference
	 * @return its tool names
	 */
	private static List<String> toolNames(Skills skills) {
		return skills.tools().stream().map((tool) -> tool.getDefinition().getName()).toList();
	}

}
