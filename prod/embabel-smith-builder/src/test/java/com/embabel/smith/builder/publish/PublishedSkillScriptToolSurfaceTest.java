/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.publish;

import java.nio.file.Path;
import java.util.List;

import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.skills.Skills;
import com.embabel.agent.skills.script.NoOpExecutionEngine;
import com.embabel.agent.skills.script.SkillScriptExecutionEngine;
import com.embabel.smith.builder.lifecycle.LifecycleStamper;
import com.embabel.smith.builder.skill.EvalSuite;
import com.embabel.smith.builder.skill.Skill;
import com.embabel.smith.builder.skill.SkillContentHash;
import com.embabel.smith.builder.skill.SkillScript;
import com.embabel.smith.builder.skill.script.GraalJsSkillScriptExecutionEngine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The seam where the two halves of this change meet, asserted from the published artifact
 * rather than from a fixture: a skill folder exactly as {@link SkillPublishable} left it,
 * loaded through Embabel's own {@link Skills}, read as the tool list a reader's model
 * would be offered.
 *
 * <p>
 * <b>Why this is not the engine's tool-surface test again.</b>
 * {@code skill/script/SkillScriptToolSurfaceTest} loads a folder it built itself, to the
 * layout it expects. The publisher builds its folder independently, and the two agree
 * only because {@link SkillScript#FOLDER} names the one directory the loader looks in. A
 * publisher that wrote its scripts somewhere else would keep every publish test green —
 * the files are still in the folder, under another name — and keep the engine's tests
 * green, because that test never reads a published folder. Only this one notices, which
 * is the whole reason it exists. It also settles the tool's <em>name</em> on the
 * published artifact: Embabel joins the skill name and the script's file name without
 * sanitizing either, so a hyphenated skill gives
 * {@code cold-store-goods-receipt_check-temperature} — hyphens included, unlike the
 * sanitized prefix the activation tools use.
 *
 * <p>
 * <b>The absent engine is the containment half.</b> Embabel filters a skill's scripts by
 * {@code engine.supportedLanguages()} and its default {@link NoOpExecutionEngine} claims
 * none, so the same published folder is inert text for a reader that installs nothing.
 * That is the state an ordinary build leaves its readers in, and it stays a test rather
 * than a comment: the engine is a bean behind
 * {@code smith.builder.skill-scripts.enabled}, and what a published folder becomes is
 * decided by whoever loads it, not by this module.
 *
 * <p>
 * Offline: no model, no endpoint. {@code Skills.tools()} is what a platform would be
 * offered. Every test here also activates the skill by name and requires its body back,
 * because Embabel's {@code loadAll} quietly skips a skill whose body references a file
 * the folder does not contain — and a test that only read {@code tools()} would report
 * that refusal to load as containment.
 *
 * @author Ivan Rodriguez
 */
class PublishedSkillScriptToolSurfaceTest {

	private static final String NAME = "cold-store-goods-receipt";

	/**
	 * Embabel's script tool name: the skill name, an underscore, and the script's file
	 * name without its extension — with the hyphens of both left as they were published.
	 */
	private static final String SCRIPT_TOOL = "cold-store-goods-receipt_check-temperature";

	/** A body that names the script it ships with, as the generator is told to. */
	private static final String SCRIPTED_BODY = "## Workflow\n1. Read the carrier temperature record.\n"
			+ "2. Run scripts/check-temperature.js and quote what it printed.\n";

	/** A body with nothing to run, which therefore names no file. */
	private static final String PLAIN_BODY = "## Workflow\n1. Read the carrier temperature record.\n"
			+ "2. Reject a breach and escalate.\n";

	private static final String SCRIPT = "const limit = Number(args[0] ?? '4');\n"
			+ "const reading = Number(stdin.trim());\n" + "if (!Number.isFinite(reading)) {\n"
			+ "  console.error('no temperature reading on stdin');\n" + "  process.exit(2);\n" + "}\n"
			+ "console.log(reading > limit ? 'reject and escalate' : 'accept and file the record');\n";

	private static final EvalSuite EVALS = new EvalSuite(NAME, List.of(new EvalSuite.Eval(1, "receive a chilled pallet",
			"accepted after a check", List.of(), List.of("must apply FEFO"))));

	@Test
	void aReaderThatInstallsTheEngineIsOfferedThePublishedScriptAsATool(@TempDir Path library) throws Exception {
		publish(library, new Skill(NAME, "Receive chilled goods and reject a temperature breach.", SCRIPTED_BODY,
				List.of(new SkillScript("scripts/check-temperature.js", SCRIPT))));

		Skills withEngine = load(library, new GraalJsSkillScriptExecutionEngine());

		// The published folder is enough to make the script callable: no declaration in
		// SKILL.md,
		// nothing the builder ran, just scripts/check-temperature.js where the Agent
		// Skills format
		// says it goes. And the folder did load, so the tool below is a decision and not
		// an
		// absence.
		assertThat(activate(withEngine)).contains("Read the carrier temperature record");
		assertThat(toolNames(withEngine)).contains(SCRIPT_TOOL);
	}

	@Test
	void aReaderThatInstallsNoEngineReadsTheSameScriptAsTextRatherThanATool(@TempDir Path library) throws Exception {
		publish(library, new Skill(NAME, "Receive chilled goods and reject a temperature breach.", SCRIPTED_BODY,
				List.of(new SkillScript("scripts/check-temperature.js", SCRIPT))));

		Skills withoutEngine = load(library, NoOpExecutionEngine.INSTANCE);

		// The containment claim, on the published artifact: installing this module's jar
		// does not
		// install a callable script. The folder loaded and the skill activated — its body
		// is
		// readable text, and the script sits in it unmentioned by the tool list.
		assertThat(activate(withoutEngine)).contains("Read the carrier temperature record");
		assertThat(toolNames(withoutEngine)).as("no script tool without an engine installed")
			.doesNotContain(SCRIPT_TOOL)
			.contains("activate");
	}

	@Test
	void aPublishedSkillWithoutScriptsOffersTheSameToolsWhicheverEngineIsInstalled(@TempDir Path library)
			throws Exception {
		publish(library, new Skill(NAME, "Receive chilled goods and reject a temperature breach.", PLAIN_BODY));

		Skills withEngine = load(library, new GraalJsSkillScriptExecutionEngine());
		Skills withoutEngine = load(library, NoOpExecutionEngine.INSTANCE);

		// An instructions-only skill is what an ordinary build publishes today, so the
		// engine has
		// to be invisible to it: the same tools in the same order, and the folder loaded
		// both ways.
		assertThat(activate(withoutEngine)).contains("Read the carrier temperature record");
		assertThat(toolNames(withEngine)).isEqualTo(toolNames(withoutEngine))
			.doesNotContain(SCRIPT_TOOL)
			.contains("activate");
	}

	private static void publish(Path library, Skill skill) throws Exception {
		new FolderPublisher(PublishTarget.parse("folder:" + library)).publish(new SkillPublishable(skill, EVALS,
				new LifecycleStamper().stamp(SkillContentHash.of(skill, EVALS), 0.75)), false, false);
	}

	/**
	 * The published folder as its reader sees it: the directory it was published into,
	 * loaded by Embabel's own directory loader with one engine installed.
	 * @param libraryRoot the directory the publisher published into, which is the parent
	 * {@code withLocalSkills} scans.
	 * @param engine the engine that reader installed.
	 * @return the skills reference an agent would be handed.
	 */
	private static Skills load(Path libraryRoot, SkillScriptExecutionEngine engine) {
		return new Skills("smith-published-skill", "A published skill folder as its reader loads it", List.of())
			.withLocalSkills(libraryRoot.toString())
			.withScriptExecutionEngine(engine);
	}

	private static List<String> toolNames(Skills skills) {
		return skills.tools().stream().map((tool) -> tool.getDefinition().getName()).toList();
	}

	/**
	 * What the platform gets when the model activates this skill by name. The loader
	 * skips a skill it cannot load rather than failing, so this is how a test tells an
	 * inert-but- loaded skill apart from a folder that was never read at all.
	 * @param skills the loaded pack.
	 * @return the activation text, or the not-found answer.
	 */
	private static String activate(Skills skills) {
		Tool activate = skills.tools()
			.stream()
			.filter((tool) -> tool.getDefinition().getName().equals("activate"))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("the pack offers no activate tool"));
		Tool.Result result = activate.call("{\"name\": \"" + NAME + "\"}");
		if (result instanceof Tool.Result.Error error) {
			return "[tool error] " + error.getMessage();
		}
		if (result instanceof Tool.Result.Text text) {
			return text.getContent();
		}
		return String.valueOf(result);
	}

}
