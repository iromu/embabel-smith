/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.embabel.agent.api.reference.LlmReference;
import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.skills.script.SkillScriptExecutionEngine;
import com.embabel.smith.builder.core.ReferenceAwarePromptCaller;
import com.embabel.smith.builder.lifecycle.LifecycleStamper;
import com.embabel.smith.builder.publish.FolderPublisher;
import com.embabel.smith.builder.publish.PublishTarget;
import com.embabel.smith.builder.publish.SkillPublishable;
import com.embabel.smith.builder.skill.EvalSuite;
import com.embabel.smith.builder.skill.Skill;
import com.embabel.smith.builder.skill.SkillContentHash;
import com.embabel.smith.builder.skill.SkillScript;
import com.embabel.smith.builder.skill.script.GraalJsSkillScriptExecutionEngine;
import picocli.CommandLine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code smith skill-run}: the route that makes a published skill folder something a
 * request is answered with, rather than only something a build wrote.
 *
 * <p>
 * <b>What the recording caller is for.</b> The claim this file exists to keep is that the
 * published folder's tools <em>reached a model call</em> — not that a {@code Skills}
 * could be built from it, which the host's own test already says. So the caller is
 * substituted at the model boundary and nowhere else: the host, the loader, the published
 * bytes and the engine all stay the ones the command wired, and the only thing replaced
 * is the thing that would otherwise need an endpoint.
 *
 * <p>
 * The two states differ by the engine the launcher was able to ask for, exactly as they
 * do on a build: with one, the script's tool is in the list the request carried; without
 * it, the skill still activates and the body still reads back and the script is not
 * callable.
 *
 * @author Ivan Rodriguez
 */
class SkillRunCommandTest {

	private static final String NAME = "cold-store-goods-receipt";

	private static final String SCRIPT_TOOL = "cold-store-goods-receipt_check-temperature";

	private static final String SCRIPTED_BODY = "## Workflow\n1. Read the carrier temperature record.\n"
			+ "2. Run scripts/check-temperature.js and quote what it printed.\n";

	private static final String SCRIPT = "console.log('checked');\n";

	private static final EvalSuite EVALS = new EvalSuite(NAME, List.of(new EvalSuite.Eval(1, "receive a chilled pallet",
			"accepted after a check", List.of(), List.of("must apply FEFO"))));

	@Test
	void theRequestCarriesThePublishedSkillAndItsScriptTool(@TempDir Path library) {
		publish(library, skill(SCRIPTED_BODY, "scripts/check-temperature.js", SCRIPT));
		Recording command = new Recording(new GraalJsSkillScriptExecutionEngine());

		Run result = run(command, library);

		assertThat(result.exit()).isZero();
		assertThat(command.systems()).containsExactly("");
		assertThat(command.request()).isEqualTo("a chilled pallet arrived at 9 degrees");
		assertThat(toolNames(command.carried())).contains("activate", "listResources", "readResource", SCRIPT_TOOL);
		assertThat(activate(command.carried())).contains("Read the carrier temperature record");
		assertThat(result.output()).contains("skills loaded: " + NAME);
	}

	@Test
	void withNoEngineTheSameRequestCarriesTheSkillButNotItsScript(@TempDir Path library) {
		publish(library, skill(SCRIPTED_BODY, "scripts/check-temperature.js", SCRIPT));
		Recording command = new Recording(null);

		Run result = run(command, library);

		// The same route, the same reference, one fewer tool: the absence belongs to the
		// engine the launcher could not obtain, and the run is otherwise identical — the
		// skill
		// activates and its body reads back, so this is not a folder that failed to load.
		assertThat(result.exit()).isZero();
		assertThat(command.carried()).as("the route still attaches the published skill").isNotNull();
		assertThat(activate(command.carried())).contains("Read the carrier temperature record");
		assertThat(toolNames(command.carried())).contains("activate").doesNotContain(SCRIPT_TOOL);
	}

	@Test
	void aFolderTheLoaderRefusedIsNamedAndNotOffered(@TempDir Path library) {
		publish(library, skill(SCRIPTED_BODY, "scripts/check-temperature.js", SCRIPT));
		publish(library, new Skill("dangling-references", "A skill whose body names a file it lacks.",
				"## Workflow\n1. Read references/indicators.md and apply it.\n"));
		Recording command = new Recording(new GraalJsSkillScriptExecutionEngine());

		Run result = run(command, library);

		// The refused folder is said out loud rather than left in the loader's log, and
		// the
		// run still answers the request with what did load — the good skill is offered,
		// the
		// refused one is not, so a missing skill cannot read as a skill that declined.
		assertThat(result.exit()).isZero();
		assertThat(result.output()).contains("skipped:").contains("dangling-references");
		assertThat(result.output()).doesNotContain("skills loaded: " + NAME + ", dangling-references");
		assertThat(toolNames(command.carried())).contains(SCRIPT_TOOL);
		assertThat(activate(command.carried(), "dangling-references")).as("the refused skill is not offered")
			.containsIgnoringCase("not found");
	}

	@Test
	void aDirectoryWithNothingLoadableRefusesInsteadOfAnsweringBlind(@TempDir Path library) {
		publish(library, new Skill("dangling-references", "A skill whose body names a file it lacks.",
				"## Workflow\n1. Read references/indicators.md and apply it.\n"));
		Recording command = new Recording(new GraalJsSkillScriptExecutionEngine());

		Run result = run(command, library);

		// No model call: an answer built on a folder nothing loaded would read as the
		// skill
		// having been consulted.
		assertThat(result.exit()).isEqualTo(1);
		assertThat(command.carried()).isNull();
		assertThat(result.output()).contains("skipped:").contains("dangling-references");
	}

	private static Skill skill(String body, String scriptPath, String scriptSource) {
		return new Skill(NAME, "Receive chilled goods and reject a temperature breach.", body,
				List.of(new SkillScript(scriptPath, scriptSource)));
	}

	private static void publish(Path library, Skill skill) {
		try {
			new FolderPublisher(PublishTarget.parse("folder:" + library)).publish(new SkillPublishable(skill, EVALS,
					new LifecycleStamper().stamp(SkillContentHash.of(skill, EVALS), 0.75)), false, false);
		}
		catch (Exception couldNotPublish) {
			throw new IllegalStateException(couldNotPublish);
		}
	}

	private static Run run(Recording command, Path library) {
		PrintStream original = System.out;
		PrintStream originalErr = System.err;
		ByteArrayOutputStream captured = new ByteArrayOutputStream();
		System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
		System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
		try {
			int exit = new CommandLine(command).execute("--skill-dir", library.toString(),
					"a chilled pallet arrived at 9 degrees");
			return new Run(exit, captured.toString(StandardCharsets.UTF_8));
		}
		finally {
			System.setOut(original);
			System.setErr(originalErr);
		}
	}

	/** What one CLI invocation did: its exit code and everything it printed. */
	private record Run(int exit, String output) {

	}

	private static List<String> toolNames(LlmReference reference) {
		return (reference == null) ? List.of()
				: reference.tools().stream().map((tool) -> tool.getDefinition().getName()).toList();
	}

	private static String activate(LlmReference reference, String skillName) {
		Tool activate = reference.tools()
			.stream()
			.filter((tool) -> tool.getDefinition().getName().equals("activate"))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("the request carried no activate tool"));
		Tool.Result result = activate.call("{\"name\": \"" + skillName + "\"}");
		if (result instanceof Tool.Result.Error error) {
			return "[tool error] " + error.getMessage();
		}
		if (result instanceof Tool.Result.Text text) {
			return text.getContent();
		}
		return String.valueOf(result);
	}

	private static String activate(LlmReference reference) {
		return activate(reference, NAME);
	}

	/**
	 * The command with only its model call replaced, recording what the request arrived
	 * carrying. Everything upstream of it — the published folder, the loader, the engine
	 * the launcher passed in — stays the code under test.
	 */
	private static final class Recording extends SkillRunCommand {

		private final List<String> systems = new ArrayList<>();

		private final List<LlmReference> carried = new ArrayList<>();

		private String request;

		Recording(SkillScriptExecutionEngine engine) {
			super(null, engine);
		}

		@Override
		protected ReferenceAwarePromptCaller caller() {
			return (system, user, reference) -> {
				this.systems.add(system);
				this.carried.add(reference);
				this.request = user;
				return "reject and escalate";
			};
		}

		private List<String> systems() {
			return List.copyOf(this.systems);
		}

		private LlmReference carried() {
			return this.carried.isEmpty() ? null : this.carried.get(0);
		}

		private String request() {
			return this.request;
		}

	}

}
