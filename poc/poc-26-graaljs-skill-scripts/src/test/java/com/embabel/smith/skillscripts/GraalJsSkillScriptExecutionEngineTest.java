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
import java.util.List;

import com.embabel.agent.skills.script.ScriptArtifact;
import com.embabel.agent.skills.script.ScriptExecutionResult;
import com.embabel.agent.skills.script.ScriptLanguage;
import com.embabel.agent.skills.script.SkillScript;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The engine contract, pinned offline: a JavaScript skill script runs in this JVM, its stdout,
 * stderr and exit code come back the way a CLI's would, and a hostile or runaway script is
 * stopped rather than trusted.
 * @author Ivan Rodriguez
 */
class GraalJsSkillScriptExecutionEngineTest {

	private final GraalJsSkillScriptExecutionEngine engine = new GraalJsSkillScriptExecutionEngine();

	private ScriptExecutionResult run(String source) {
		Path dir = SkillFixtures.skillWithScript("engine-case", "run.js", source);
		return this.engine.execute(SkillFixtures.script(dir, "run.js"), List.of(), "", List.of());
	}

	private static String stdoutOf(ScriptExecutionResult result) {
		assertThat(result).isInstanceOf(ScriptExecutionResult.Success.class);
		return ((ScriptExecutionResult.Success) result).getStdout();
	}

	@Test
	void onlySupportsJavaScript() {
		assertThat(this.engine.supportedLanguages()).containsExactly(ScriptLanguage.JAVASCRIPT);
	}

	@Test
	void runsAScriptAndCapturesConsoleOutput() {
		ScriptExecutionResult result = run("console.log('total', 1 + 2); console.error('a warning')");

		assertThat(result).isInstanceOf(ScriptExecutionResult.Success.class);
		ScriptExecutionResult.Success success = (ScriptExecutionResult.Success) result;
		assertThat(success.getStdout()).isEqualTo("total 3\n");
		assertThat(success.getStderr()).isEqualTo("a warning\n");
		assertThat(success.getExitCode()).isZero();
		assertThat(success.getArtifacts()).isEmpty();
	}

	@Test
	void handsTheScriptItsArgsAndStdin() {
		Path dir = SkillFixtures.skillWithScript("args-case", "run.js", """
				const order = JSON.parse(stdin);
				console.log(order.id + '|' + args.join(',') + '|' + args.length + '|' + Array.isArray(args));
				""");

		ScriptExecutionResult result = this.engine.execute(SkillFixtures.script(dir, "run.js"),
				List.of("--discount=10", "--approval=ana"), "{\"id\":\"ORD-7\"}", List.of());

		assertThat(stdoutOf(result)).isEqualTo("ORD-7|--discount=10,--approval=ana|2|true\n");
	}

	@Test
	void reportsTheExitCodeFromProcessExit() {
		ScriptExecutionResult result = run("console.log('before'); process.exit(3); console.log('unreachable')");

		assertThat(result).isInstanceOf(ScriptExecutionResult.Success.class);
		ScriptExecutionResult.Success success = (ScriptExecutionResult.Success) result;
		assertThat(success.getExitCode()).isEqualTo(3);
		// Everything before the exit still reached the caller; everything after did not run.
		assertThat(success.getStdout()).isEqualTo("before\n");
	}

	@Test
	void exposesArgvAndAnEmptyEnvironment() {
		ScriptExecutionResult result = run("console.log(process.argv.length + ':' + JSON.stringify(process.env))");

		assertThat(stdoutOf(result)).isEqualTo("0:{}\n");
	}

	@Test
	void reportsAGuestErrorAsAFailureWithItsMessage() {
		ScriptExecutionResult result = run("console.log('partial'); throw new TypeError('bad invoice')");

		assertThat(result).isInstanceOf(ScriptExecutionResult.Failure.class);
		ScriptExecutionResult.Failure failure = (ScriptExecutionResult.Failure) result;
		assertThat(failure.getError()).contains("bad invoice");
		assertThat(failure.getExitCode()).isEqualTo(1);
		assertThat(failure.getTimedOut()).isFalse();
	}

	@Test
	void reportsASyntaxErrorAsAFailure() {
		ScriptExecutionResult result = run("const = ;");

		assertThat(result).isInstanceOf(ScriptExecutionResult.Failure.class);
		assertThat(((ScriptExecutionResult.Failure) result).getError()).isNotBlank();
	}

	@Test
	void killsASpinningScriptAtTheTimeLimit() {
		Path dir = SkillFixtures.skillWithScript("spin", "run.js", "let i = 0; while (true) { i++; }");
		// Wall-clock limit only: the statement limit is off, so the watchdog is what stops it.
		GraalJsSkillScriptExecutionEngine patient = new GraalJsSkillScriptExecutionEngine(400L, 0L, 10_000, 100_000,
				Path.of(System.getProperty("java.io.tmpdir")));

		long started = System.nanoTime();
		ScriptExecutionResult result = patient.execute(SkillFixtures.script(dir, "run.js"), List.of(), "", List.of());
		long tookMillis = (System.nanoTime() - started) / 1_000_000L;

		assertThat(result).isInstanceOf(ScriptExecutionResult.Failure.class);
		ScriptExecutionResult.Failure failure = (ScriptExecutionResult.Failure) result;
		assertThat(failure.getTimedOut()).isTrue();
		assertThat(failure.getError()).contains("time limit");
		assertThat(tookMillis).isBetween(300L, 5_000L);
	}

	@Test
	void stopsAScriptAtTheStatementLimitDeterministically() {
		Path dir = SkillFixtures.skillWithScript("statements", "run.js", "let i = 0; while (true) { i++; }");
		GraalJsSkillScriptExecutionEngine bounded = new GraalJsSkillScriptExecutionEngine(30_000L, 200_000L, 10_000,
				100_000, Path.of(System.getProperty("java.io.tmpdir")));

		ScriptExecutionResult first = bounded.execute(SkillFixtures.script(dir, "run.js"), List.of(), "", List.of());
		ScriptExecutionResult second = bounded.execute(SkillFixtures.script(dir, "run.js"), List.of(), "", List.of());

		assertThat(first).isInstanceOf(ScriptExecutionResult.Failure.class);
		String error = ((ScriptExecutionResult.Failure) first).getError();
		assertThat(error).contains("statement limit");
		// The same script fails the same way twice — the bound is deterministic, not a race.
		assertThat(((ScriptExecutionResult.Failure) second).getError()).isEqualTo(error);
	}

	@Test
	void blocksHostAccessFromGuestCode(@TempDir Path tempDir) {
		Path marker = tempDir.resolve("pwned");
		ScriptExecutionResult result = run("""
				const rt = Java.type('java.lang.Runtime');
				rt.getRuntime().exec('touch %s');
				console.log('should never print');
				""".formatted(marker.toString()));

		assertThat(result).isInstanceOf(ScriptExecutionResult.Failure.class);
		assertThat(((ScriptExecutionResult.Failure) result).getError()).contains("not allowed");
		assertThat(marker).doesNotExist();
	}

	@Test
	void givesTheScriptACliSurfaceRatherThanANodeOne() {
		ScriptExecutionResult result = run("""
				console.log([typeof process, typeof process.argv, typeof require, typeof module, typeof __dirname]
						.join('/'));
				""");

		// process exists because the engine defines it; Node's module globals deliberately do not.
		assertThat(stdoutOf(result)).isEqualTo("object/object/undefined/undefined/undefined\n");
	}

	@Test
	void readsOnlyTheFilesStagedForTheRun(@TempDir Path tempDir) throws IOException {
		Path staged = tempDir.resolve("invoice.txt");
		Files.writeString(staged, "net=100", StandardCharsets.UTF_8);
		Path dir = SkillFixtures.skillWithScript("read-case", "run.js",
				"console.log(readFile('invoice.txt') + '/' + readFile('invoice.txt').length)");

		ScriptExecutionResult result = this.engine.execute(SkillFixtures.script(dir, "run.js"), List.of(), "",
				List.of(staged));

		assertThat(stdoutOf(result)).isEqualTo("net=100/7\n");
	}

	@Test
	void refusesToReadAFileThatWasNotStaged() {
		Path dir = SkillFixtures.skillWithScript("read-deny", "run.js", "console.log(readFile('/etc/passwd'))");

		ScriptExecutionResult result = this.engine.execute(SkillFixtures.script(dir, "run.js"), List.of(), "",
				List.of());

		assertThat(result).isInstanceOf(ScriptExecutionResult.Failure.class);
		assertThat(((ScriptExecutionResult.Failure) result).getError()).contains("was not staged");
	}

	@Test
	void returnsFilesTheScriptWroteAsArtifacts(@TempDir Path tempDir) {
		GraalJsSkillScriptExecutionEngine engine = new GraalJsSkillScriptExecutionEngine(5_000L, 25_000_000L, 100_000,
				512_000L, tempDir);
		Path dir = SkillFixtures.skillWithScript("write-case", "run.js",
				"writeFile('summary.csv', 'a,b\\n1,2\\n'); console.log('written')");

		ScriptExecutionResult result = engine.execute(SkillFixtures.script(dir, "run.js"), List.of(), "", List.of());

		assertThat(result).isInstanceOf(ScriptExecutionResult.Success.class);
		List<ScriptArtifact> artifacts = ((ScriptExecutionResult.Success) result).getArtifacts();
		assertThat(artifacts).hasSize(1);
		assertThat(artifacts.get(0).getName()).isEqualTo("summary.csv");
		assertThat(artifacts.get(0).getMimeType()).isEqualTo("text/csv");
		assertThat(artifacts.get(0).getSizeBytes()).isEqualTo(8L);
	}

	@Test
	void capsRunawayOutputSoAScriptCannotFloodThePrompt() {
		GraalJsSkillScriptExecutionEngine capped = new GraalJsSkillScriptExecutionEngine(5_000L, 25_000_000L, 200,
				512_000L, Path.of(System.getProperty("java.io.tmpdir")));
		Path dir = SkillFixtures.skillWithScript("flood", "run.js", "for (let i = 0; i < 5000; i++) { console.log('x'.repeat(80)); }");

		ScriptExecutionResult result = capped.execute(SkillFixtures.script(dir, "run.js"), List.of(), "", List.of());

		assertThat(result).isInstanceOf(ScriptExecutionResult.Success.class);
		String stdout = ((ScriptExecutionResult.Success) result).getStdout();
		assertThat(stdout).contains("output truncated at 200 chars");
	}

	@Test
	void deniesAScriptInALanguageItDoesNotRun() {
		Path dir = SkillFixtures.skillWithScript("py-case", "run.py", "print('hi')");
		SkillScript python = new SkillScript("py-case", "run.py", ScriptLanguage.PYTHON, dir);

		ScriptExecutionResult result = this.engine.execute(python, List.of(), "", List.of());

		assertThat(result).isInstanceOf(ScriptExecutionResult.Denied.class);
		assertThat(((ScriptExecutionResult.Denied) result).getReason()).contains("JavaScript only");
	}

	@Test
	void deniesAMissingScriptFile() {
		Path dir = SkillFixtures.skillWithScript("missing", "present.js", "console.log(1)");

		ScriptExecutionResult result = this.engine.execute(SkillFixtures.script(dir, "absent.js"), List.of(), "",
				List.of());

		assertThat(result).isInstanceOf(ScriptExecutionResult.Denied.class);
		assertThat(((ScriptExecutionResult.Denied) result).getReason()).contains("Script not found");
	}

	@Test
	void deniesAScriptOverTheSizeLimit() {
		Path dir = SkillFixtures.skillWithScript("huge", "run.js", "console.log('" + "x".repeat(2_000) + "')");
		GraalJsSkillScriptExecutionEngine tiny = new GraalJsSkillScriptExecutionEngine(5_000L, 25_000_000L, 100_000,
				500L, Path.of(System.getProperty("java.io.tmpdir")));

		ScriptExecutionResult result = tiny.execute(SkillFixtures.script(dir, "run.js"), List.of(), "", List.of());

		assertThat(result).isInstanceOf(ScriptExecutionResult.Denied.class);
		assertThat(((ScriptExecutionResult.Denied) result).getReason()).contains("byte limit");
	}

	@Test
	void runsAnEsModuleScriptWithTopLevelAwait() {
		Path dir = SkillFixtures.skillWithScript("esm-case", "run.mjs", """
				const { value } = await Promise.resolve({ value: 42 });
				console.log('esm value=' + value);
				""");

		ScriptExecutionResult result = this.engine.execute(SkillFixtures.script(dir, "run.mjs"), List.of(), "",
				List.of());

		assertThat(stdoutOf(result)).isEqualTo("esm value=42\n");
	}

}
