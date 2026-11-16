/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.skillscripts;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two engines must agree on the answer, so the timing comparison is between two
 * things that did the same job — and the in-JVM one must not depend on the host having an
 * interpreter.
 *
 * @author Ivan Rodriguez
 */
class ScriptEngineComparisonTest {

	private final List<ScriptEngineComparison.EngineRun> rows = new ScriptEngineComparison(
			new GraalJsSkillScriptExecutionEngine(), Path.of(System.getProperty("java.io.tmpdir")))
		.compare();

	@Test
	void theInJvmEngineRunsTheScriptWhetherOrNotTheHostHasAnInterpreter() {
		ScriptEngineComparison.EngineRun inJvm = row("graaljs (in-JVM)");

		assertThat(inJvm.available()).isTrue();
		assertThat(inJvm.exitCode()).isZero();
		assertThat(inJvm.detail()).startsWith("longest collatz");
	}

	@Test
	void bothEnginesProduceTheSameAnswerWhenBothCanRun() {
		ScriptEngineComparison.EngineRun external = row("process (external node)");
		assertThat(external.available())
			.as("node is on this host, so the external engine should have run: " + external.detail())
			.isTrue();

		assertThat(row("graaljs (in-JVM)").detail()).isEqualTo(external.detail());
	}

	@Test
	void theReportRendersBothEnginesWithTheirSubstrate() {
		String markdown = ScriptEngineComparison.toMarkdown(rows);

		assertThat(markdown).contains("| Engine | Runs the script in | Exit | Median (ms) | Result |")
			.contains("graaljs (in-JVM)")
			.contains("process (external node)");
	}

	private ScriptEngineComparison.EngineRun row(String engine) {
		return this.rows.stream()
			.filter((run) -> run.engine().equals(engine))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("No row for " + engine));
	}

}
