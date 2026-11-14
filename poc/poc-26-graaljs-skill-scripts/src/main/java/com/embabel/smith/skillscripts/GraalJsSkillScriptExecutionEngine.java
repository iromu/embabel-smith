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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.embabel.agent.skills.script.ScriptArtifact;
import com.embabel.agent.skills.script.ScriptExecutionResult;
import com.embabel.agent.skills.script.ScriptLanguage;
import com.embabel.agent.skills.script.SkillScript;
import com.embabel.agent.skills.script.SkillScriptExecutionEngine;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.ResourceLimits;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

/**
 * Runs an Agent Skill's {@code scripts/*.js} <em>inside</em> the JVM on embedded GraalJS,
 * implementing Embabel's {@link SkillScriptExecutionEngine} SPI.
 *
 * <p>
 * Embabel's Agent Skills implementation recognises {@code .js} scripts
 * ({@link ScriptLanguage#JAVASCRIPT}) but ships only engines that hand the script to something
 * outside the JVM — {@code ProcessSkillScriptExecutionEngine} spawns {@code node}, the container
 * engines spawn Docker or Podman — and defaults to {@code NoOpExecutionEngine}, which logs a
 * warning and refuses to run anything. This engine removes that dependency: GraalJS is a Java
 * library, so a JavaScript skill script runs in the agent's own process on a plain JDK, with no
 * Node installation, no container runtime and no GraalVM distribution.
 *
 * <p>
 * Each {@link #execute} gets a fresh, hardened {@link Context}: no host class lookup, no host
 * method access except the single {@link Bridge} below, no threads, no processes, no environment,
 * no filesystem. The script sees the CLI-shaped globals documented on {@link #PROLOGUE} and
 * nothing else. Two limits bound a runaway script: a wall-clock watchdog that cancels the context,
 * and a deterministic statement count that fails the same script the same way every run.
 *
 * @author Ivan Rodriguez
 */
public final class GraalJsSkillScriptExecutionEngine implements SkillScriptExecutionEngine {

	/** The tool-group-free name reported in the POC's output. */
	public static final String NAME = "graaljs-in-jvm";

	/** Default wall-clock limit for one script run. */
	public static final long DEFAULT_TIMEOUT_MILLIS = 5_000L;

	/** Default guest statement count; the deterministic sibling of the wall-clock limit. */
	public static final long DEFAULT_STATEMENT_LIMIT = 25_000_000L;

	/** Default cap on captured stdout/stderr per run. */
	public static final int DEFAULT_MAX_OUTPUT_CHARS = 100_000;

	/** Default cap on the size of a script we are willing to read. */
	public static final long DEFAULT_MAX_SCRIPT_BYTES = 512_000L;

	/**
	 * The globals every script is given, evaluated into the context before the script body. They
	 * are defined here rather than bound as host objects so the script surface stays small and the
	 * only host object reachable from guest code is {@link Bridge}.
	 * <ul>
	 * <li>{@code args} — the arguments the model passed, as a JS array of strings</li>
	 * <li>{@code stdin} — the piped input, as a string</li>
	 * <li>{@code console.log/info/debug} — captured as stdout; {@code warn/error} as stderr</li>
	 * <li>{@code process.argv} / {@code process.exit(code)} / {@code process.env} (empty)</li>
	 * <li>{@code readFile(name)} — read one of the {@code inputFiles} staged for this run</li>
	 * <li>{@code writeFile(name, content)} — write into this run's output dir, returned as an
	 * artifact</li>
	 * </ul>
	 */
	static final String PROLOGUE = """
			globalThis.process = {
			  argv: args,
			  env: {},
			  exit: (code) => __bridge.exit(code),
			};
			globalThis.readFile = (name) => __bridge.readFile(name);
			globalThis.writeFile = (name, content) => __bridge.writeFile(name, content);
			""";

	private static final Set<ScriptLanguage> SUPPORTED = Set.of(ScriptLanguage.JAVASCRIPT);

	private final long timeoutMillis;

	private final long statementLimit;

	private final int maxOutputChars;

	private final long maxScriptBytes;

	private final Path workDir;

	/**
	 * Create an engine.
	 * @param timeoutMillis wall-clock limit per run; the context is cancelled when it expires
	 * @param statementLimit guest statement count before the run fails, or {@code 0} to disable
	 * @param maxOutputChars cap on captured stdout plus stderr
	 * @param maxScriptBytes refuse to read a script larger than this
	 * @param workDir parent directory for each run's artifact output directory
	 */
	public GraalJsSkillScriptExecutionEngine(long timeoutMillis, long statementLimit, int maxOutputChars,
			long maxScriptBytes, Path workDir) {
		this.timeoutMillis = timeoutMillis;
		this.statementLimit = statementLimit;
		this.maxOutputChars = maxOutputChars;
		this.maxScriptBytes = maxScriptBytes;
		this.workDir = workDir;
	}

	/**
	 * Create an engine with the POC's defaults and a temp working directory.
	 */
	public GraalJsSkillScriptExecutionEngine() {
		this(DEFAULT_TIMEOUT_MILLIS, DEFAULT_STATEMENT_LIMIT, DEFAULT_MAX_OUTPUT_CHARS, DEFAULT_MAX_SCRIPT_BYTES,
				Path.of(System.getProperty("java.io.tmpdir")));
	}

	@Override
	public Set<ScriptLanguage> supportedLanguages() {
		return SUPPORTED;
	}

	@Override
	public ScriptExecutionResult.Denied validate(SkillScript script) {
		if (!SUPPORTED.contains(script.getLanguage())) {
			return new ScriptExecutionResult.Denied(
					NAME + " runs JavaScript only; this script is " + script.getLanguage());
		}
		Path path = script.getScriptPath();
		if (!Files.isRegularFile(path)) {
			return new ScriptExecutionResult.Denied("Script not found: " + path);
		}
		try {
			long size = Files.size(path);
			if (size > this.maxScriptBytes) {
				return new ScriptExecutionResult.Denied(
						"Script is " + size + " bytes, over the " + this.maxScriptBytes + " byte limit");
			}
		}
		catch (IOException ex) {
			return new ScriptExecutionResult.Denied("Could not stat script " + path + ": " + ex.getMessage());
		}
		return null;
	}

	@Override
	public ScriptExecutionResult execute(SkillScript script, List<String> args, String stdin,
			List<? extends Path> inputFiles) {
		ScriptExecutionResult.Denied denied = validate(script);
		if (denied != null) {
			return denied;
		}

		String source;
		try {
			source = Files.readString(script.getScriptPath(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			return ScriptExecutionResults.failure("Could not read script: " + ex.getMessage(), "", 1, false, 0L);
		}

		Path outputDir = createOutputDir(script);
		CappedOutputStream stdout = new CappedOutputStream(this.maxOutputChars);
		CappedOutputStream stderr = new CappedOutputStream(this.maxOutputChars);
		Bridge bridge = new Bridge(inputFiles, outputDir);
		AtomicBoolean timedOut = new AtomicBoolean();

		Context.Builder builder = Context.newBuilder("js")
			.option("engine.WarnInterpreterOnly", "false")
			.allowHostAccess(HostAccess.EXPLICIT)
			.allowHostClassLookup((name) -> false)
			.allowCreateThread(false)
			.allowNativeAccess(false)
			.allowCreateProcess(false)
			.allowEnvironmentAccess(EnvironmentAccess.NONE)
			.allowIO(false)
			.out(stdout)
			.err(stderr);
		if (this.statementLimit > 0) {
			builder.resourceLimits(ResourceLimits.newBuilder().statementLimit(this.statementLimit, null).build());
		}

		long started = System.nanoTime();
		Context context = builder.build();
		Thread watchdog = startWatchdog(context, timedOut);
		int exitCode = 0;
		try {
			bind(context, args, stdin, bridge);
			context.eval(Source.newBuilder("js", PROLOGUE, "skill-prologue.js").build());
			Value result = context.eval(scriptSource(script, source));
			if (result.isNumber() && result.fitsInInt() && result.asInt() != 0) {
				// A script's last expression doubles as its exit code, like a CLI.
				exitCode = result.asInt();
			}
		}
		catch (PolyglotException ex) {
			ScriptExit exit = scriptExit(ex);
			if (exit != null) {
				exitCode = exit.code;
			}
			else {
				long took = elapsed(started);
				// The statement limit surfaces as a cancelled execution too, so it has to be
				// tested before the watchdog's own cancellation.
				if (ex.isResourceExhausted()) {
					return ScriptExecutionResults.failure("Script exceeded the statement limit of "
							+ this.statementLimit + ": " + ex.getMessage(), stderr.text(), null, true, took);
				}
				if (timedOut.get() || ex.isCancelled()) {
					return ScriptExecutionResults.failure(
							"Script exceeded the " + this.timeoutMillis + "ms time limit", stderr.text(), null, true,
							took);
				}
				return ScriptExecutionResults.failure(describe(ex), stderr.text(), 1, false, took);
			}
		}
		catch (IOException ex) {
			return ScriptExecutionResults.failure("Could not compile " + script.getFileName() + ": "
					+ ex.getMessage(), stderr.text(), 1, false, elapsed(started));
		}
		catch (RuntimeException ex) {
			return ScriptExecutionResults.failure(ex.toString(), stderr.text(), 1, false, elapsed(started));
		}
		finally {
			watchdog.interrupt();
			closeQuietly(context);
			stdout.closeQuietly();
			stderr.closeQuietly();
		}

		return ScriptExecutionResults.success(stdout.text(), stderr.text(), exitCode, elapsed(started),
				collectArtifacts(outputDir));
	}

	private void bind(Context context, List<String> args, String stdin, Bridge bridge) {
		Value bindings = context.getBindings("js");
		// Built guest-side so the script receives a real JS array, not a host list proxy.
		Value push = context.eval("js", "(array, value) => { array.push(value); return array; }");
		Value guestArgs = context.eval("js", "[]");
		for (String arg : (args != null) ? args : List.<String>of()) {
			guestArgs = push.execute(guestArgs, arg);
		}
		bindings.putMember("args", guestArgs);
		bindings.putMember("stdin", (stdin != null) ? stdin : "");
		bindings.putMember("__bridge", bridge);
	}

	private Thread startWatchdog(Context context, AtomicBoolean timedOut) {
		Thread watchdog = new Thread(() -> {
			try {
				Thread.sleep(this.timeoutMillis);
			}
			catch (InterruptedException ignored) {
				Thread.currentThread().interrupt();
				return;
			}
			timedOut.set(true);
			try {
				context.close(true);
			}
			catch (RuntimeException ignored) {
				// Cancelling a context that already finished is expected.
			}
		}, "graaljs-watchdog");
		watchdog.setDaemon(true);
		watchdog.start();
		return watchdog;
	}

	private static void closeQuietly(Context context) {
		try {
			context.close(false);
		}
		catch (RuntimeException ignored) {
			// A cancelled context throws on close; the run's outcome is already decided.
		}
	}

	/**
	 * An {@code .mjs} script is compiled as an ES module, which is what grants it
	 * {@code import}/{@code export} and top-level {@code await}; a {@code .js} script is compiled
	 * as a plain script, matching how a CLI file behaves under Node.
	 * @param script the script to compile
	 * @param source its source text
	 * @return the GraalJS source to evaluate.
	 */
	private static Source scriptSource(SkillScript script, String source) {
		Source.Builder builder = Source.newBuilder("js", source, script.getScriptPath().toString());
		if (script.getFileName().endsWith(".mjs")) {
			builder.mimeType("application/javascript+module");
		}
		try {
			return builder.build();
		}
		catch (IOException ex) {
			throw new IllegalStateException("Could not compile " + script.getFileName() + ": " + ex.getMessage(), ex);
		}
	}

	/**
	 * Find a {@code process.exit(code)} that surfaced through the polyglot boundary. GraalJS wraps
	 * a host exception as a guest error, so it may arrive as the cause or as a host exception
	 * depending on how deep in the script the exit was called.
	 * @param ex the polyglot exception to inspect
	 * @return the requested exit, or {@code null} if this was a genuine failure
	 */
	private static ScriptExit scriptExit(PolyglotException ex) {
		Throwable host = ex.isHostException() ? ex.asHostException() : ex;
		for (Throwable t = host; t != null; t = t.getCause()) {
			if (t instanceof ScriptExit exit) {
				return exit;
			}
			if (t.getCause() == t) {
				break;
			}
		}
		return null;
	}

	private static String describe(PolyglotException ex) {
		String message = ex.getMessage();
		return (message == null || message.isBlank()) ? ex.getClass().getSimpleName() : message;
	}

	private Path createOutputDir(SkillScript script) {
		try {
			String prefix = script.getSkillName() + "-" + script.getFileName().replaceAll("[^A-Za-z0-9._-]", "_") + "-";
			return Files.createTempDirectory(this.workDir, prefix);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to create the artifact output directory: " + ex.getMessage(), ex);
		}
	}

	private static List<ScriptArtifact> collectArtifacts(Path outputDir) {
		if (!Files.isDirectory(outputDir)) {
			return List.of();
		}
		List<ScriptArtifact> artifacts = new ArrayList<>();
		try (var paths = Files.list(outputDir)) {
			for (Path file : paths.filter(Files::isRegularFile).sorted().toList()) {
				artifacts.add(new ScriptArtifact(file.getFileName().toString(), file,
						ScriptArtifact.Companion.inferMimeType(file.getFileName().toString()), Files.size(file),
						UUID.randomUUID().toString(), Instant.now()));
			}
		}
		catch (IOException ex) {
			return List.of();
		}
		return artifacts;
	}

	private static long elapsed(long startedNanos) {
		return (System.nanoTime() - startedNanos) / 1_000_000L;
	}

	/** Thrown by {@code process.exit(code)} to unwind the guest stack. */
	public static final class ScriptExit extends RuntimeException {

		private final int code;

		ScriptExit(int code) {
			super("process.exit(" + code + ")", null, false, false);
			this.code = code;
		}

		/**
		 * The exit code the script asked for.
		 * @return the code passed to {@code process.exit}.
		 */
		public int getCode() {
			return this.code;
		}

	}

	/**
	 * The only host object reachable from a skill script. Every method is explicitly exported, so
	 * nothing else on the host classpath is callable from guest code.
	 */
	public final class Bridge {

		private final List<? extends Path> inputFiles;

		private final Path outputDir;

		Bridge(List<? extends Path> inputFiles, Path outputDir) {
			this.inputFiles = (inputFiles != null) ? inputFiles : List.of();
			this.outputDir = outputDir;
		}

		/**
		 * Stop the script with an exit code.
		 * @param code the exit code to report
		 */
		@HostAccess.Export
		public void exit(int code) {
			throw new ScriptExit(code);
		}

		/**
		 * Read a file staged for this run. Only the names staged as {@code inputFiles} are readable,
		 * by basename, so a script cannot reach the wider filesystem.
		 * @param name the file's basename
		 * @return the file contents
		 */
		@HostAccess.Export
		public String readFile(String name) {
			String wanted = Path.of(name).getFileName().toString();
			for (Path candidate : this.inputFiles) {
				if (candidate.getFileName().toString().equals(wanted)) {
					try {
						return Files.readString(candidate, StandardCharsets.UTF_8);
					}
					catch (IOException ex) {
						throw new IllegalStateException("Could not read " + wanted + ": " + ex.getMessage(), ex);
					}
				}
			}
			throw new IllegalArgumentException(
					"readFile: '" + wanted + "' was not staged for this run; staged files are " + stagedNames());
		}

		/**
		 * Write a file into this run's output directory. It comes back as a
		 * {@link ScriptArtifact}.
		 * @param name the file's basename; any path is stripped
		 * @param content the contents
		 */
		@HostAccess.Export
		public void writeFile(String name, String content) {
			String wanted = Path.of(name).getFileName().toString();
			try {
				Files.writeString(this.outputDir.resolve(wanted), content, StandardCharsets.UTF_8);
			}
			catch (IOException ex) {
				throw new IllegalStateException("Could not write " + wanted + ": " + ex.getMessage(), ex);
			}
		}

		private String stagedNames() {
			return this.inputFiles.stream().map((p) -> p.getFileName().toString()).toList().toString();
		}

	}

	/** An output sink that stops accepting bytes past a cap, so a script cannot flood the prompt. */
	static final class CappedOutputStream extends OutputStream {

		private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

		private final int maxChars;

		private boolean truncated;

		CappedOutputStream(int maxChars) {
			this.maxChars = maxChars;
		}

		@Override
		public void write(int b) {
			if (this.buffer.size() < this.maxChars) {
				this.buffer.write(b);
			}
			else {
				this.truncated = true;
			}
		}

		@Override
		public void write(byte[] bytes, int offset, int length) {
			int room = this.maxChars - this.buffer.size();
			if (length > room) {
				this.buffer.write(bytes, offset, Math.max(room, 0));
				this.truncated = true;
			}
			else {
				this.buffer.write(bytes, offset, length);
			}
		}

		String text() {
			String text = this.buffer.toString(StandardCharsets.UTF_8);
			return this.truncated ? text + "\n... [output truncated at " + this.maxChars + " chars]" : text;
		}

		void closeQuietly() {
			try {
				this.buffer.close();
			}
			catch (IOException ignored) {
				// ByteArrayOutputStream.close() is a no-op.
			}
		}

	}

}
