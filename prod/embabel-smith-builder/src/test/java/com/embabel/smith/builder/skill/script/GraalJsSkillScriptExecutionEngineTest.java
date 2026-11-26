/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.skill.script;

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
 * The engine contract, pinned offline: a JavaScript skill script runs in this JVM, its
 * stdout, stderr and exit code come back the way a CLI's would, and the three things a
 * script can do that would put the host at risk — name a host class, read the
 * environment, spawn a process — fail inside the sandbox instead of reaching anything.
 *
 * <p>
 * Every case here is a property the change claims, and each one fails if the
 * corresponding guardrail is removed: the sandbox assertions fail if a
 * {@code Context.Builder} flag is relaxed, the two runaway cases fail if a limit is
 * dropped, and the exit-code cases fail if the failure stops being reported as an exit
 * rather than as an exception.
 *
 * @author Ivan Rodriguez
 */
class GraalJsSkillScriptExecutionEngineTest {

	private final GraalJsSkillScriptExecutionEngine engine = new GraalJsSkillScriptExecutionEngine();

	private ScriptExecutionResult run(String source) {
		Path pack = SkillScriptFixtures.skillPack("engine-case", "run.js", source);
		return this.engine.execute(SkillScriptFixtures.script(pack, "engine-case", "run.js"), List.of(), "", List.of());
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
		Path pack = SkillScriptFixtures.skillPack("args-case", "run.js", """
				const order = JSON.parse(stdin);
				console.log(order.id + '|' + args.join(',') + '|' + args.length + '|' + Array.isArray(args));
				""");

		ScriptExecutionResult result = this.engine.execute(SkillScriptFixtures.script(pack, "args-case", "run.js"),
				List.of("--discount=10", "--approval=ana"), "{\"id\":\"ORD-7\"}", List.of());

		assertThat(stdoutOf(result)).isEqualTo("ORD-7|--discount=10,--approval=ana|2|true\n");
	}

	@Test
	void reportsTheExitCodeFromProcessExit() {
		ScriptExecutionResult result = run("console.log('before'); process.exit(2); console.log('unreachable')");

		assertThat(result).isInstanceOf(ScriptExecutionResult.Success.class);
		ScriptExecutionResult.Success success = (ScriptExecutionResult.Success) result;
		assertThat(success.getExitCode()).isEqualTo(2);
		// What the script printed before it exited still reached the caller; what came
		// after did not run.
		assertThat(success.getStdout()).isEqualTo("before\n");
	}

	@Test
	void reportsAGuestErrorAsANonZeroExitShapedFailureNotAStack() {
		ScriptExecutionResult result = run("console.log('partial'); throw new TypeError('bad invoice')");

		assertThat(result).isInstanceOf(ScriptExecutionResult.Failure.class);
		ScriptExecutionResult.Failure failure = (ScriptExecutionResult.Failure) result;
		assertThat(failure.getError()).contains("bad invoice");
		// The shape a CLI returns when a script raises: an exit code, not a trace.
		assertThat(failure.getExitCode()).isEqualTo(1);
		assertThat(failure.getTimedOut()).isFalse();
		// No Java stack into the loop: the message is the guest's own, and it carries no
		// frame of this module's.
		assertThat(failure.getError()).doesNotContain("com.embabel.smith").doesNotContain("\tat ");
	}

	@Test
	void reportsASyntaxErrorAsAFailure() {
		ScriptExecutionResult result = run("const = ;");

		assertThat(result).isInstanceOf(ScriptExecutionResult.Failure.class);
		assertThat(((ScriptExecutionResult.Failure) result).getError()).isNotBlank();
	}

	@Test
	void deniesHostClassLookupAndLeavesNoTraceOnDisk(@TempDir Path tempDir) {
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
	void deniesAnEnvironmentReadFromGuestCode() {
		// Two routes to the environment: the CLI global the engine defines, and the JDK
		// class that would actually answer. The first is empty by construction; the
		// second is refused rather than answered.
		ScriptExecutionResult emptyEnv = run("console.log(JSON.stringify(process.env))");
		assertThat(stdoutOf(emptyEnv)).isEqualTo("{}\n");

		ScriptExecutionResult attempt = run("console.log(Java.type('java.lang.System').getenv('PATH'))");
		assertThat(attempt).isInstanceOf(ScriptExecutionResult.Failure.class);
		assertThat(((ScriptExecutionResult.Failure) attempt).getError()).contains("not allowed");
	}

	@Test
	void deniesSpawningAProcessFromGuestCode(@TempDir Path tempDir) {
		Path marker = tempDir.resolve("spawned");
		ScriptExecutionResult result = run("""
				const pb = Java.type('java.lang.ProcessBuilder');
				new pb(['touch', '%s']).start();
				""".formatted(marker.toString()));

		assertThat(result).isInstanceOf(ScriptExecutionResult.Failure.class);
		assertThat(((ScriptExecutionResult.Failure) result).getError()).contains("not allowed");
		assertThat(marker).doesNotExist();
	}

	@Test
	void deniesThreadCreationFromGuestCode() {
		ScriptExecutionResult result = run("""
				const t = Java.type('java.lang.Thread');
				t.ofVirtual().start(function() { console.log('ran off-thread'); });
				""");

		assertThat(result).isInstanceOf(ScriptExecutionResult.Failure.class);
		assertThat(((ScriptExecutionResult.Failure) result).getError()).contains("not allowed");
	}

	@Test
	void killsASpinningScriptAtTheTimeLimit() {
		Path pack = SkillScriptFixtures.skillPack("spin", "run.js", "let i = 0; while (true) { i++; }");
		// Wall-clock limit only: the statement limit is off, so the watchdog is what
		// stops
		// it. Removing that watchdog turns this test into a hang rather than a green run.
		GraalJsSkillScriptExecutionEngine patient = new GraalJsSkillScriptExecutionEngine(400L, 0L, 10_000, 100_000,
				Path.of(System.getProperty("java.io.tmpdir")));

		long started = System.nanoTime();
		ScriptExecutionResult result = patient.execute(SkillScriptFixtures.script(pack, "spin", "run.js"), List.of(),
				"", List.of());
		long tookMillis = (System.nanoTime() - started) / 1_000_000L;

		assertThat(result).isInstanceOf(ScriptExecutionResult.Failure.class);
		ScriptExecutionResult.Failure failure = (ScriptExecutionResult.Failure) result;
		assertThat(failure.getTimedOut()).isTrue();
		assertThat(failure.getError()).contains("time limit");
		assertThat(tookMillis).isBetween(300L, 10_000L);
	}

	@Test
	void stopsAScriptAtTheStatementLimitDeterministically() {
		Path pack = SkillScriptFixtures.skillPack("statements", "run.js", "let i = 0; while (true) { i++; }");
		GraalJsSkillScriptExecutionEngine bounded = new GraalJsSkillScriptExecutionEngine(30_000L, 200_000L, 10_000,
				100_000, Path.of(System.getProperty("java.io.tmpdir")));

		ScriptExecutionResult first = bounded.execute(SkillScriptFixtures.script(pack, "statements", "run.js"),
				List.of(), "", List.of());
		ScriptExecutionResult second = bounded.execute(SkillScriptFixtures.script(pack, "statements", "run.js"),
				List.of(), "", List.of());

		assertThat(first).isInstanceOf(ScriptExecutionResult.Failure.class);
		String error = ((ScriptExecutionResult.Failure) first).getError();
		assertThat(error).contains("statement limit");
		// The bound is deterministic, not a race: the same script fails the same way
		// twice.
		assertThat(((ScriptExecutionResult.Failure) second).getError()).isEqualTo(error);
	}

	@Test
	void givesTheScriptACliSurfaceRatherThanANodeOne() {
		ScriptExecutionResult result = run("""
				console.log([typeof process, typeof process.argv, typeof require, typeof module, typeof __dirname]
						.join('/'));
				""");

		// process exists because the engine defines it; Node's module globals
		// deliberately
		// do not, so a script that assumes Node fails at generation rather than on a
		// reader's machine.
		assertThat(stdoutOf(result)).isEqualTo("object/object/undefined/undefined/undefined\n");
	}

	@Test
	void readsAFileStagedForTheRun(@TempDir Path tempDir) throws IOException {
		Path staged = tempDir.resolve("invoice.txt");
		Files.writeString(staged, "net=100", StandardCharsets.UTF_8);
		Path pack = SkillScriptFixtures.skillPack("read-case", "run.js",
				"console.log(readFile('invoice.txt') + '/' + readFile('invoice.txt').length)");

		ScriptExecutionResult result = this.engine.execute(SkillScriptFixtures.script(pack, "read-case", "run.js"),
				List.of(), "", List.of(staged));

		assertThat(stdoutOf(result)).isEqualTo("net=100/7\n");

	}

	@Test
	void readsAFileOfTheSkillSOwnFolder() {
		Path pack = SkillScriptFixtures.skillPack("own-folder", "run.js",
				"console.log(readFile('references/tax.json'))");
		try {
			Files.createDirectories(pack.resolve("own-folder").resolve("references"));
			Files.writeString(pack.resolve("own-folder").resolve("references").resolve("tax.json"), "{\"rate\":0.19}",
					StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}

		ScriptExecutionResult result = this.engine.execute(SkillScriptFixtures.script(pack, "own-folder", "run.js"),
				List.of(), "", List.of());

		assertThat(stdoutOf(result)).isEqualTo("{\"rate\":0.19}\n");
	}

	@Test
	void refusesAFileOutsideTheSkillFolderAndTheStagedSet() {
		Path pack = SkillScriptFixtures.skillPack("read-deny", "run.js", "console.log(readFile('/etc/passwd'))");

		ScriptExecutionResult outside = this.engine.execute(SkillScriptFixtures.script(pack, "read-deny", "run.js"),
				List.of(), "", List.of());
		assertThat(outside).isInstanceOf(ScriptExecutionResult.Failure.class);
		assertThat(((ScriptExecutionResult.Failure) outside).getError()).contains("neither staged");

		Path escape = SkillScriptFixtures.skillPack("read-escape", "run.js",
				"console.log(readFile('../../../../../../etc/passwd'))");
		ScriptExecutionResult walked = this.engine.execute(SkillScriptFixtures.script(escape, "read-escape", "run.js"),
				List.of(), "", List.of());
		assertThat(walked).isInstanceOf(ScriptExecutionResult.Failure.class);
		assertThat(((ScriptExecutionResult.Failure) walked).getError()).contains("neither staged");
	}

	@Test
	void writesOnlyIntoTheRunOutputDirAndReturnsItAsArtifacts(@TempDir Path tempDir) {
		GraalJsSkillScriptExecutionEngine engine = new GraalJsSkillScriptExecutionEngine(5_000L, 25_000_000L, 100_000,
				512_000L, tempDir);
		Path pack = SkillScriptFixtures.skillPack("write-case", "run.js",
				"writeFile('summary.csv', 'a,b\\n1,2\\n'); console.log('written')");

		ScriptExecutionResult result = engine.execute(SkillScriptFixtures.script(pack, "write-case", "run.js"),
				List.of(), "", List.of());

		assertThat(result).isInstanceOf(ScriptExecutionResult.Success.class);
		List<ScriptArtifact> artifacts = ((ScriptExecutionResult.Success) result).getArtifacts();
		assertThat(artifacts).hasSize(1);
		assertThat(artifacts.get(0).getName()).isEqualTo("summary.csv");
		assertThat(artifacts.get(0).getMimeType()).isEqualTo("text/csv");
		assertThat(artifacts.get(0).getSizeBytes()).isEqualTo(8L);
		// The script cannot write back into the skill it came from.
		assertThat(pack.resolve("write-case").resolve("scripts").resolve("summary.csv")).doesNotExist();
	}

	@Test
	void capsRunawayOutputSoAScriptCannotFloodThePrompt() {
		GraalJsSkillScriptExecutionEngine capped = new GraalJsSkillScriptExecutionEngine(5_000L, 25_000_000L, 200,
				512_000L, Path.of(System.getProperty("java.io.tmpdir")));
		Path pack = SkillScriptFixtures.skillPack("flood", "run.js",
				"for (let i = 0; i < 5000; i++) { console.log('x'.repeat(80)); }");

		ScriptExecutionResult result = capped.execute(SkillScriptFixtures.script(pack, "flood", "run.js"), List.of(),
				"", List.of());

		assertThat(result).isInstanceOf(ScriptExecutionResult.Success.class);
		assertThat(((ScriptExecutionResult.Success) result).getStdout()).contains("output truncated at 200 chars");
	}

	@Test
	void deniesAScriptInALanguageItDoesNotRun() {
		Path pack = SkillScriptFixtures.skillPack("py-case", "run.py", "print('hi')");
		SkillScript python = new SkillScript("py-case", "run.py", ScriptLanguage.PYTHON, pack.resolve("py-case"));

		ScriptExecutionResult result = this.engine.execute(python, List.of(), "", List.of());

		assertThat(result).isInstanceOf(ScriptExecutionResult.Denied.class);
		assertThat(((ScriptExecutionResult.Denied) result).getReason()).contains("JavaScript only");
	}

	@Test
	void deniesAMissingScriptFile() {
		Path pack = SkillScriptFixtures.skillPack("missing", "present.js", "console.log(1)");

		ScriptExecutionResult result = this.engine.execute(SkillScriptFixtures.script(pack, "missing", "absent.js"),
				List.of(), "", List.of());

		assertThat(result).isInstanceOf(ScriptExecutionResult.Denied.class);
		assertThat(((ScriptExecutionResult.Denied) result).getReason()).contains("Script not found");
	}

	@Test
	void deniesAScriptOverTheSizeLimit() {
		Path pack = SkillScriptFixtures.skillPack("huge", "run.js", "console.log('" + "x".repeat(2_000) + "')");
		GraalJsSkillScriptExecutionEngine tiny = new GraalJsSkillScriptExecutionEngine(5_000L, 25_000_000L, 100_000,
				500L, Path.of(System.getProperty("java.io.tmpdir")));

		ScriptExecutionResult result = tiny.execute(SkillScriptFixtures.script(pack, "huge", "run.js"), List.of(), "",
				List.of());

		assertThat(result).isInstanceOf(ScriptExecutionResult.Denied.class);
		assertThat(((ScriptExecutionResult.Denied) result).getReason()).contains("byte limit");
	}

	@Test
	void runsAnEsModuleScriptWithTopLevelAwait() {
		Path pack = SkillScriptFixtures.skillPack("esm-case", "run.mjs", """
				const { value } = await Promise.resolve({ value: 42 });
				console.log('esm value=' + value);
				""");

		ScriptExecutionResult result = this.engine.execute(SkillScriptFixtures.script(pack, "esm-case", "run.mjs"),
				List.of(), "", List.of());

		assertThat(stdoutOf(result)).isEqualTo("esm value=42\n");
	}

}
