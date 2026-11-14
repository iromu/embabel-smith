/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.embabel.smith.skillscripts;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.embabel.agent.skills.script.ProcessSkillScriptExecutionEngine;
import com.embabel.agent.skills.script.ScriptExecutionResult;
import com.embabel.agent.skills.script.ScriptLanguage;
import com.embabel.agent.skills.script.SkillScript;
import com.embabel.agent.skills.script.SkillScriptExecutionEngine;

/**
 * Measures the in-JVM engine against the engine Embabel actually ships for JavaScript —
 * {@link ProcessSkillScriptExecutionEngine}, which spawns an external {@code node}.
 *
 * <p>
 * The script under test is pure CPU with console output, so the comparison is about the
 * execution substrate rather than the script's own work: one run pays the cost of starting an
 * OS process per script call, the other pays nothing per call because the engine is already
 * loaded in the agent's process. It also shows the dependency each one has — with no
 * {@code node} on the host, the external engine cannot run a {@code .js} skill script at all
 * while the in-JVM engine can.
 *
 * @author Ivan Rodriguez
 */
public final class ScriptEngineComparison {

	/**
	 * Upper bound of the collatz sweep. Chosen so the script also fits under the in-JVM
	 * engine's statement guardrail, letting both engines finish the same workload; at
	 * {@code 100000} the guardrail is what stops the interpreter run, which is a finding in
	 * itself rather than something to hide behind a bigger number.
	 */
	static final int SWEEP_TO = 10_000;

	/**
	 * A CPU-bound script that needs no CLI input, so both engines can run the identical workload.
	 */
	static final String CPU_SCRIPT = """
			function collatz(n) {
			  let steps = 0;
			  while (n !== 1) {
			    n = (n % 2 === 0) ? n / 2 : 3 * n + 1;
			    steps++;
			  }
			  return steps;
			}
			let longest = 0;
			let at = 0;
			for (let i = 2; i < $SWEEP_TO; i++) {
			  const steps = collatz(i);
			  if (steps > longest) {
			    longest = steps;
			    at = i;
			  }
			}
			console.log('longest collatz ' + longest + ' at ' + at);
			""".replace("$SWEEP_TO", Integer.toString(SWEEP_TO));

	private static final int RUNS = 3;

	private final GraalJsSkillScriptExecutionEngine inJvmEngine;

	private final Path workDir;

	/**
	 * Compare an in-JVM engine with an external-interpreter one.
	 * @param inJvmEngine the GraalJS engine to compare against the external one
	 * @param workDir where the comparison script is written
	 */
	public ScriptEngineComparison(GraalJsSkillScriptExecutionEngine inJvmEngine, Path workDir) {
		this.inJvmEngine = inJvmEngine;
		this.workDir = workDir;
	}

	/**
	 * Run the comparison script on both engines.
	 * @return one row per engine.
	 */
	public List<EngineRun> compare() {
		Path skillDir = prepareSkillDir();
		SkillScript script = new SkillScript("engine-comparison", "collatz.js", ScriptLanguage.JAVASCRIPT, skillDir);

		List<EngineRun> runs = new ArrayList<>();
		runs.add(measure("graaljs (in-JVM)", "the agent's own JVM", this.inJvmEngine, script, false));
		runs.add(measure("process (external node)", "a spawned node process", new ProcessSkillScriptExecutionEngine(),
				script, true));
		return runs;
	}

	private EngineRun measure(String name, String substrate, SkillScriptExecutionEngine engine, SkillScript script,
			boolean needsExternalInterpreter) {
		if (needsExternalInterpreter && !nodeAvailable()) {
			return new EngineRun(name, substrate, false, null, -1L,
					"no 'node' executable on this host — an external interpreter cannot run a .js skill script");
		}
		List<Long> millis = new ArrayList<>();
		String detail = "";
		Integer exitCode = null;
		for (int run = 0; run < RUNS; run++) {
			long started = System.nanoTime();
			ScriptExecutionResult result = engine.execute(script, List.of(), "", List.of());
			millis.add((System.nanoTime() - started) / 1_000_000L);
			if (result instanceof ScriptExecutionResult.Success success) {
				exitCode = success.getExitCode();
				detail = success.getStdout().trim();
			}
			else if (result instanceof ScriptExecutionResult.Failure failure) {
				exitCode = (failure.getExitCode() != null) ? failure.getExitCode() : -1;
				detail = failure.getError();
			}
			else if (result instanceof ScriptExecutionResult.Denied denied) {
				return new EngineRun(name, substrate, false, null, -1L, "denied: " + denied.getReason());
			}
		}
		long[] sorted = millis.stream().mapToLong(Long::longValue).sorted().toArray();
		boolean ran = (exitCode != null) && exitCode == 0;
		return new EngineRun(name, substrate, ran, exitCode, sorted[sorted.length / 2], detail);
	}

	/**
	 * Report whether an external interpreter is available at all.
	 * @return whether a {@code node} interpreter is on the PATH.
	 */
	public static boolean nodeAvailable() {
		try {
			Process process = new ProcessBuilder("node", "--version").redirectErrorStream(true).start();
			return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
		}
		catch (IOException | InterruptedException ex) {
			if (ex instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			return false;
		}
	}

	/**
	 * Render the comparison as a Markdown table.
	 * @param rows the measured rows
	 * @return the Markdown.
	 */
	public static String toMarkdown(List<EngineRun> rows) {
		StringBuilder table = new StringBuilder();
		table.append("| Engine | Runs the script in | Exit | Median (ms) | Result |\n");
		table.append("|--------|------------------|:----:|------------:|--------|\n");
		for (EngineRun row : rows) {
			table.append("| ").append(row.engine()).append(" | ").append(row.substrate()).append(" | ")
				.append((row.exitCode() == null) ? "—" : row.exitCode()).append(" | ")
				.append((row.medianMillis() < 0) ? "—" : row.medianMillis()).append(" | ")
				.append(clip(row.detail())).append(" |\n");
		}
		return table.toString();
	}

	private Path prepareSkillDir() {
		try {
			Path dir = Files.createTempDirectory(this.workDir, "engine-comparison-");
			Files.createDirectories(dir.resolve("scripts"));
			Files.writeString(dir.resolve("SKILL.md"), """
					---
					name: engine-comparison
					description: CPU-bound script used to compare skill-script engines.
					---

					Generated by POC 26.
					""", StandardCharsets.UTF_8);
			Files.writeString(dir.resolve("scripts").resolve("collatz.js"), CPU_SCRIPT, StandardCharsets.UTF_8);
			return dir;
		}
		catch (IOException ex) {
			throw new IllegalStateException("Could not prepare the comparison script: " + ex.getMessage(), ex);
		}
	}

	private static String clip(String text) {
		if (text == null || text.isBlank()) {
			return "—";
		}
		String flat = text.replaceAll("\\s+", " ").trim();
		return (flat.length() > 90) ? flat.substring(0, 90) + "…" : flat;
	}

	/**
	 * The outcome of one engine running the comparison script.
	 * @param engine the engine's name
	 * @param substrate where the script ran
	 * @param available whether the engine could run it at all
	 * @param exitCode the exit code, or {@code null} when it never ran
	 * @param medianMillis the median wall-clock time over {@link #RUNS} runs
	 * @param detail the output on success, or the reason it could not run
	 * @author Ivan Rodriguez
	 */
	public record EngineRun(String engine, String substrate, boolean available, Integer exitCode, long medianMillis,
			String detail) {
	}

}
