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

import com.embabel.agent.skills.script.ScriptExecutionResult;
import com.embabel.agent.skills.script.ScriptLanguage;
import com.embabel.agent.skills.script.SkillScript;
import com.embabel.agent.skills.script.SkillScriptExecutionEngine;

/**
 * Measures which ECMAScript 2026 features the in-JVM engine actually provides, by running each
 * one as a real skill script through {@link SkillScriptExecutionEngine} — not by asking GraalJS
 * what it claims to support.
 *
 * <p>
 * Probes are written as ES modules ({@code .mjs}) because several of them need
 * {@code await}, which the engine grants only in module mode.
 *
 * @author Ivan Rodriguez
 */
public final class Es2026Conformance {

	/**
	 * The ES2026 set as published when edition 17 was approved (June 2026), plus three adjacent
	 * features a real script reaches for and whose absence would show up as a runtime error
	 * rather than a spec argument.
	 */
	public static final List<Probe> PROBES = List.of(
			new Probe("Math.sumPrecise", "ES2026", "Math.sumPrecise([0.1, 0.2, 0.3]) === 0.6"),
			new Probe("Iterator.concat", "ES2026", "[...Iterator.concat([1, 2], [3, 4])].join(',') === '1,2,3,4'"),
			new Probe("Array.fromAsync", "ES2026",
					"(await Array.fromAsync([1, 2, 3], async (x) => x * 2)).join(',') === '2,4,6'"),
			new Probe("Error.isError", "ES2026",
					"Error.isError(new TypeError('x')) && !Error.isError({ message: 'x' })"),
			new Probe("Map.prototype.getOrInsert", "ES2026", "new Map().getOrInsert('k', 42) === 42"),
			new Probe("Map.prototype.getOrInsertComputed", "ES2026",
					"new Map().getOrInsertComputed('k', () => [1, 2]).length === 2"),
			new Probe("WeakMap.prototype.getOrInsert", "ES2026",
					"(() => { const wm = new WeakMap(); const key = {}; return wm.getOrInsert(key, 'v') === 'v' && wm.getOrInsert(key, 'other') === 'v'; })()"),
			new Probe("Uint8Array to/fromBase64 and to/fromHex", "ES2026",
					"Uint8Array.fromHex('00ff').toBase64() === 'AP8=' && Uint8Array.fromBase64('AP8=').toHex() === '00ff'"),
			new Probe("JSON.rawJSON", "ES2026", "JSON.stringify(JSON.rawJSON('123456789012345678901234567890'))"
					+ " === '123456789012345678901234567890'"),
			new Probe("JSON.parse reviver source argument", "ES2026",
					"(() => { let seen; JSON.parse('[1, 2]', (key, value, source) => { seen = source; return value; }); return seen !== undefined; })()"),
			new Probe("Float16Array", "ES2026", "new Float16Array([1.5])[0] === 1.5"),
			new Probe("Iterator helpers", "ES2025", "[1, 2, 3].values().map((x) => x * 2).toArray().join(',') === '2,4,6'"),
			new Probe("Object.groupBy", "ES2024", "Object.keys(Object.groupBy([1, 2, 3], (x) => x % 2 ? 'odd' : 'even'))"
					+ ".sort().join(',') === 'even,odd'"),
			new Probe("Top-level await (ESM)", "ES2022", "(await Promise.resolve('ok')) === 'ok'"));

	private final SkillScriptExecutionEngine engine;

	private final Path workDir;

	/**
	 * Measure one engine against the probe list.
	 * @param engine the engine to measure
	 * @param workDir where probe scripts are written
	 */
	public Es2026Conformance(SkillScriptExecutionEngine engine, Path workDir) {
		this.engine = engine;
		this.workDir = workDir;
	}

	/**
	 * Run every probe through the engine.
	 * @return one row per probe, in declaration order.
	 */
	public List<Row> run() {
		Path skillDir = prepareSkillDir();
		List<Row> rows = new ArrayList<>();
		int index = 0;
		for (Probe probe : PROBES) {
			String fileName = "probe_" + (++index) + ".mjs";
			write(scriptFile(skillDir, fileName), probeScript(probe));
			ScriptExecutionResult result = this.engine.execute(
					new SkillScript("es2026-conformance", fileName, ScriptLanguage.JAVASCRIPT, skillDir), List.of(), "",
					List.of());
			rows.add(rowFor(probe, result));
		}
		return rows;
	}

	private static Row rowFor(Probe probe, ScriptExecutionResult result) {
		if (result instanceof ScriptExecutionResult.Success success) {
			boolean passed = success.getStdout().contains("PASS") && success.getExitCode() == 0;
			return new Row(probe, passed, passed ? "" : firstLine(success.getStdout() + success.getStderr()));
		}
		if (result instanceof ScriptExecutionResult.Failure failure) {
			return new Row(probe, false, firstLine(failure.getError()));
		}
		if (result instanceof ScriptExecutionResult.Denied denied) {
			return new Row(probe, false, "denied: " + firstLine(denied.getReason()));
		}
		return new Row(probe, false, "unknown result " + result);
	}

	private static String probeScript(Probe probe) {
		return "console.log((%s) ? 'PASS' : 'FAIL');\n".formatted(probe.expression());
	}

	/**
	 * Render rows as a Markdown table for the README and the launcher's output.
	 * @param rows the measured rows
	 * @return the Markdown.
	 */
	public static String toMarkdown(List<Row> rows) {
		StringBuilder table = new StringBuilder();
		table.append("| Feature | Since | Engine |\n");
		table.append("|---------|-------|--------|\n");
		for (Row row : rows) {
			table.append("| `").append(row.probe().feature()).append("` | ").append(row.probe().since())
				.append(" | ")
				.append(row.passed() ? "supported" : "not supported — " + row.detail())
				.append(" |\n");
		}
		return table.toString();
	}

	private Path prepareSkillDir() {
		try {
			Path dir = Files.createTempDirectory(this.workDir, "es2026-conformance-");
			Files.createDirectories(dir.resolve("scripts"));
			Files.writeString(dir.resolve("SKILL.md"), """
					---
					name: es2026-conformance
					description: Probes the ECMAScript 2026 features the in-JVM skill-script engine provides.
					---

					Generated by POC 26; each probe is a one-line ES module.
					""", StandardCharsets.UTF_8);
			return dir;
		}
		catch (IOException ex) {
			throw new IllegalStateException("Could not prepare the conformance probe directory: " + ex.getMessage(),
					ex);
		}
	}

	private static Path scriptFile(Path skillDir, String fileName) {
		return skillDir.resolve("scripts").resolve(fileName);
	}

	private static void write(Path file, String content) {
		try {
			Files.writeString(file, content, StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Could not write probe " + file + ": " + ex.getMessage(), ex);
		}
	}

	private static String firstLine(String text) {
		if (text == null || text.isBlank()) {
			return "";
		}
		String trimmed = text.strip();
		int newline = trimmed.indexOf('\n');
		return (newline < 0) ? trimmed : trimmed.substring(0, newline);
	}

	/**
	 * One feature under test.
	 * @param feature the built-in the ECMAScript specification names
	 * @param since the edition that added it
	 * @param expression a JS expression that must evaluate truthy
	 * @author Ivan Rodriguez
	 */
	public record Probe(String feature, String since, String expression) {
	}

	/**
	 * The outcome of one probe.
	 * @param probe the probe that ran
	 * @param passed whether the expression was truthy
	 * @param detail the engine's own words when it did not pass
	 * @author Ivan Rodriguez
	 */
	public record Row(Probe probe, boolean passed, String detail) {
	}

}
