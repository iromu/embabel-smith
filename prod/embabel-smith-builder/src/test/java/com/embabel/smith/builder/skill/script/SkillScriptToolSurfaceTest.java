/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.skill.script;

import java.util.List;

import com.embabel.agent.skills.Skills;
import com.embabel.agent.skills.script.NoOpExecutionEngine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The property the engine exists for, read off the tool list a model would be offered
 * rather than off this module's source: a skill that carries a script gains a callable
 * tool <em>only</em> when an engine is installed, and a failure inside the script arrives
 * as tool output rather than as an exception in the loop.
 *
 * <p>
 * <b>The control.</b> The absent-tool half is the load-bearing one. Embabel filters a
 * skill's scripts by {@code engine.supportedLanguages()}, and its default
 * {@link NoOpExecutionEngine} claims none — so a skill with scripts and no engine is
 * inert text, exactly as it was before this module knew about scripts. Asserting only the
 * positive half would leave that claim unfalsifiable: the pair is what shows the
 * difference is the engine and not the fixture. The non-script tool ({@code activate})
 * appears in both lists, so an empty list from a mis-aimed pack cannot pass as "no
 * engine".
 *
 * <p>
 * <b>Offline.</b> No model and no endpoint: {@code Skills.tools()} is what the platform
 * would be offered, and calling a tool goes through the same entry point the platform
 * calls. Whether a model then chooses to call it is a different number and stays with the
 * endpoint-gated tests.
 *
 * @author Ivan Rodriguez
 */
class SkillScriptToolSurfaceTest {

	private static final String COMPUTE = """
			const totals = { net: 1259.88, vat: 50.40 };
			console.log(JSON.stringify(totals));
			""";

	private Skills scriptPack() {
		return SkillScriptFixtures.load(SkillScriptFixtures.skillPack("totalling", "compute_totals.js", COMPUTE),
				new GraalJsSkillScriptExecutionEngine());
	}

	private static Skills packWithoutEngine() {
		return SkillScriptFixtures.load(SkillScriptFixtures.skillPack("totalling", "compute_totals.js", COMPUTE),
				NoOpExecutionEngine.INSTANCE);
	}

	@Test
	void installingTheEngineIsWhatMakesTheScriptACallableTool() {
		List<String> withEngine = SkillScriptFixtures.toolNames(scriptPack());
		List<String> withoutEngine = SkillScriptFixtures.toolNames(packWithoutEngine());

		assertThat(withEngine).as("the script tool an installed engine adds")
			.anyMatch((name) -> name.contains("compute_totals"));
		// The control: with Embabel's default engine the script is loaded but not
		// offered,
		// which is the whole reason installing an engine is a decision rather than a
		// default.
		assertThat(withoutEngine).as("no script tool without an engine installed")
			.noneMatch((name) -> name.contains("compute_totals"));
		// And the pack is loaded in both, so the difference above is the engine and not a
		// pack that failed to load.
		assertThat(withoutEngine).as("control: the same pack, loaded").contains("activate");
	}

	@Test
	void theScriptToolComputesThroughTheToolEntryPoint() {
		String output = SkillScriptFixtures.callTool(scriptPack(), "totalling_compute_totals",
				"{\"args\": [], \"stdin\": \"\", \"inputFiles\": []}");

		assertThat(output).contains("Script completed with exit code 0")
			.contains("\"net\":1259.88")
			.contains("\"vat\":50.4");
	}

	@Test
	void aScriptThatRaisesSurfacesAsToolOutputWithAnExitCodeNotAStack() {
		Skills skills = SkillScriptFixtures.load(
				SkillScriptFixtures.skillPack("raiser", "raise.js", "throw new TypeError('bad invoice')"),
				new GraalJsSkillScriptExecutionEngine());

		String output = SkillScriptFixtures.callTool(skills, "raiser_raise", "{}");

		// A CLI's answer to a script that dies: a nonzero exit, reported. What must not
		// appear is a Java frame — the loop reads tool output, not a stack trace.
		assertThat(output).contains("Script failed").contains("exit code: 1").contains("bad invoice");
		assertThat(output).doesNotContain("com.embabel.smith.builder.skill.script")
			.doesNotContain("PolyglotException")
			.doesNotContain("\tat ");
	}

	@Test
	void aScriptThatExitsNonZeroSurfacesAsToolOutputWithItsCode() {
		Skills skills = SkillScriptFixtures.load(
				SkillScriptFixtures.skillPack("rejects", "check.js",
						"console.error('REJECTED: needs an --approval'); process.exit(2)"),
				new GraalJsSkillScriptExecutionEngine());

		String output = SkillScriptFixtures.callTool(skills, "rejects_check", "{}");

		// POC 26's exit-code-2 case: a refusal is a result the model can act on, not an
		// error the loop has to survive.
		assertThat(output).contains("exit code 2").contains("REJECTED");
	}

	@Test
	void aScriptThatReachesForTheHostSurfacesAsToolOutput() {
		Skills skills = SkillScriptFixtures.load(
				SkillScriptFixtures.skillPack("hostile", "reach.js",
						"Java.type('java.lang.Runtime').getRuntime().exec('id')"),
				new GraalJsSkillScriptExecutionEngine());

		String output = SkillScriptFixtures.callTool(skills, "hostile_reach", "{}");

		assertThat(output).contains("Script failed").contains("not allowed");
	}

	@Test
	void aScriptThatNeverReturnsSurfacesAsATimedOutToolResult() {
		Skills skills = SkillScriptFixtures.load(
				SkillScriptFixtures.skillPack("spins", "spin.js", "let i = 0; while (true) { i++; }"),
				new GraalJsSkillScriptExecutionEngine(2_000L, 500_000L, 10_000, 100_000,
						java.nio.file.Path.of(System.getProperty("java.io.tmpdir"))));

		String output = SkillScriptFixtures.callTool(skills, "spins_spin", "{}");

		assertThat(output).contains("Script failed").containsAnyOf("statement limit", "time limit");
	}

}
