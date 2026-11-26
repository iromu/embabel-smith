/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.embabel.agent.skills.Skills;
import com.embabel.agent.skills.script.SkillScriptExecutionEngine;
import com.embabel.smith.builder.skill.PublishedSkillHost;
import com.embabel.smith.builder.skill.Skill;
import com.embabel.smith.builder.skill.SkillScript;
import com.embabel.smith.builder.skill.script.SkillScriptEngineConfiguration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The off-by-default switch, asserted where it is actually decided: the launcher asks a
 * bean provider for an engine and passes whatever comes back to both of its consumers.
 *
 * <p>
 * <b>Why the wiring and not the bean.</b> {@code SkillScriptEngineBeanTest} already
 * asserts the conditional on the bean, and {@code EvalRunnerScriptArmTest} asserts the
 * arm. Neither constructs the place the engine is <em>obtained</em>, so an engine
 * constructed unconditionally there — a {@code getBean} instead of a provider, or a
 * {@code new} beside it — left the whole module green. That gap is what this file closes:
 * the pair is asserted through {@link SmithBuilderApplication#scriptEngine}, the one
 * method that turns the switch into the object a host is built with, so the claim "off by
 * default" is a tested fact rather than a default value in a configuration class.
 *
 * <p>
 * <b>The observable is a tool list, not a bean.</b> A bean being absent is only the
 * means; what the switch is for is that a published script is not callable. So each state
 * ends at the tools a host built from that engine would offer, and the control that makes
 * the pair mean something is the same folder loaded both ways: the skill activates in
 * both, so the difference below cannot be a folder that loaded once and not the other.
 *
 * @author Ivan Rodriguez
 */
class SmithBuilderScriptEngineWiringTest {

	private static final String NAME = "cold-store-goods-receipt";

	private static final String SCRIPT_TOOL = "cold-store-goods-receipt_check-temperature";

	private static final String SCRIPTED_BODY = "## Workflow\n1. Read the carrier temperature record.\n"
			+ "2. Run scripts/check-temperature.js and quote what it printed.\n";

	private static final String SCRIPT = "console.log('checked');\n";

	@Test
	void withTheSwitchUnsetTheWiringConstructsNoEngineAndOffersNoScriptTool(@TempDir Path library) throws Exception {
		write(library, "scripts/check-temperature.js");
		State off = stateOf(library, null);

		// Nothing constructed, not an inert engine: the provider answered null because
		// the
		// conditional never registered a definition.
		assertThat(off.engine).as("no execution engine was constructed by that path").isNull();
		assertThat(off.tools).as("the published script is text, not a tool")
			.doesNotContain(SCRIPT_TOOL)
			.contains("activate", "listResources", "readResource");
	}

	@Test
	void withTheSwitchOnTheWiringObtainsOneEngineAndExactlyOneToolAppears(@TempDir Path library) throws Exception {
		write(library, "scripts/check-temperature.js");
		State off = stateOf(library, null);
		State on = stateOf(library, "true");

		assertThat(on.engine).as("the switch installed the engine the wiring asked for").isNotNull();
		// "Exactly one appears, and no other tool appears because of the switch" — the
		// difference between the two lists is the whole claim, so a switch that also
		// widened
		// the surface some other way would fail here rather than read as success.
		assertThat(off.tools).as("the switch adds tools, it does not swap them").allMatch(on.tools::contains);
		assertThat(difference(on.tools, off.tools)).containsExactly(SCRIPT_TOOL);
	}

	/**
	 * Load a published folder with the engine the launcher would have obtained under one
	 * value of the switch.
	 * @param library the published directory
	 * @param propertyValue the switch value, or null to leave it unset
	 * @return the engine the wiring obtained and the tools a host built from it offers
	 */
	private static State stateOf(Path library, String propertyValue) {
		ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(SkillScriptEngineConfiguration.class);
		if (propertyValue != null) {
			runner = runner.withPropertyValues(SkillScriptEngineConfiguration.ENABLED_PROPERTY + "=" + propertyValue);
		}
		AtomicReference<SkillScriptExecutionEngine> engine = new AtomicReference<>();
		AtomicReference<List<String>> tools = new AtomicReference<>();
		runner.run((context) -> {
			engine.set(SmithBuilderApplication.scriptEngine(context));
			tools.set(toolNames(new PublishedSkillHost(library, engine.get()).skills()));
		});
		return new State(engine.get(), tools.get());
	}

	private record State(SkillScriptExecutionEngine engine, List<String> tools) {

	}

	private static List<String> difference(List<String> from, List<String> baseline) {
		return from.stream().filter((tool) -> !baseline.contains(tool)).toList();
	}

	private static List<String> toolNames(Skills skills) {
		return skills.tools().stream().map((tool) -> tool.getDefinition().getName()).toList();
	}

	/**
	 * A published-shaped folder: the frontmatter and body {@code Skill.toSkillMd()}
	 * renders, and a script beside it. Written by hand rather than through the publisher
	 * because this test is about the switch, and a publisher failure would read as a
	 * switch failure.
	 * @param library the directory to publish into.
	 * @param scriptPath the script to write inside the skill folder.
	 * @throws Exception if the folder could not be written.
	 */
	private static void write(Path library, String scriptPath) throws Exception {
		Skill skill = new Skill(NAME, "Receive chilled goods and reject a temperature breach.", SCRIPTED_BODY,
				List.of(new SkillScript(scriptPath, SCRIPT)));
		Path folder = library.resolve(NAME);
		Files.createDirectories(folder.resolve("scripts"));
		Files.writeString(folder.resolve("SKILL.md"), skill.toSkillMd());
		Files.writeString(folder.resolve(scriptPath), SCRIPT);
	}

}
