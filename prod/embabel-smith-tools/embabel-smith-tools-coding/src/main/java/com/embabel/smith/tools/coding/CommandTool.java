/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.tools.coding;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.embabel.agent.api.tool.Tool;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The command tool, as the addon's own.
 *
 * <p>
 * <b>Where it comes from.</b> The executor is the {@code bash -lc} tool this repository
 * proved in {@code poc/poc-7-runtime-adapter}, extracted rather than depended on:
 * {@code poc/} is frozen evidence and the inherited enforcer ban fails the build on a
 * {@code poc-*} edge. What the POC did not have is everything this addon's own spec asks
 * for — a working directory checked against a root, a wall-clock limit, an output
 * allowance that says when it was reached — so those are what is added here.
 *
 * <p>
 * <b>Why not the platform's.</b> {@code embabel-agent-code} ships
 * {@code BashTools(String workingDirectory)} with one {@code runBashCommand(String)}
 * returning a bare string. Nothing in this repo depends on that artifact, and the shape
 * is the disqualifying part: a boot-time working directory, no time limit, no exit code.
 * A command that hangs would hang the run, and the run's whole bound is a 60-second
 * interaction timeout that a hung tool consumes silently.
 *
 * <p>
 * <b>What a result carries.</b> The exit code, whether it timed out, and captured
 * standard output and standard error, each truncated at the allowance with the truncation
 * stated in the text rather than left as a shortened answer that reads as complete. A
 * command that exits non-zero is still a successful tool call — the exit code is the
 * answer, not an error — because a build failing is information a coding step has to see.
 *
 * @author Ivan Rodriguez
 */
public final class CommandTool {

	/** The tool name a model calls. */
	public static final String RUN_COMMAND = "runCommand";

	/** The argument carrying the command line. */
	public static final String COMMAND = "command";

	/** The optional argument carrying a working directory, relative to the run root. */
	public static final String WORKING_DIRECTORY = "workingDirectory";

	/** The description a model reads for {@link #RUN_COMMAND}. */
	private static final String DESCRIPTION = "Run a shell command with bash -lc in the run's working directory "
			+ "and report its exit code, standard output and standard error. Output longer than the allowance is "
			+ "truncated and says so. The command is the whole command line; the working directory is optional and "
			+ "must stay inside the run root.";

	private static final String COMMAND_DESCRIPTION = "The command line to run, passed to bash -lc.";

	private static final String WORKING_DIRECTORY_DESCRIPTION = "Optional directory to run in, relative to the run "
			+ "root. A directory outside the run root is refused.";

	/** How long to let a killed command take to actually die before giving up on it. */
	private static final Duration KILL_GRACE = Duration.ofSeconds(5);

	/** How long to wait for the output readers after the process is gone. */
	private static final Duration READ_GRACE = Duration.ofSeconds(5);

	private static final ObjectMapper JSON = new ObjectMapper();

	private CommandTool() {
	}

	/**
	 * The command tool, bounded by one root.
	 * @param root the run root: the default working directory, and the boundary a named
	 * one must stay inside
	 * @param timeout the wall-clock limit after which the command is terminated and
	 * reported as timed out
	 * @param maxOutputChars how much of each stream is returned before the remainder is
	 * dropped with a stated truncation
	 * @return the tool the {@code shell} group carries
	 */
	public static Tool tool(RunRoot root, Duration timeout, int maxOutputChars) {
		return Tool.Companion.of(RUN_COMMAND, DESCRIPTION,
				Tool.InputSchema.of(Tool.Parameter.string(COMMAND, COMMAND_DESCRIPTION, true),
						Tool.Parameter.string(WORKING_DIRECTORY, WORKING_DIRECTORY_DESCRIPTION, false)),
				Tool.Metadata.DEFAULT, (Tool.Function) (arguments) -> call(root, timeout, maxOutputChars, arguments));
	}

	/**
	 * The tool's body: resolve the working directory, run, and render what came back. A
	 * working directory outside the root is a refusal naming path and root; everything
	 * else is an answer.
	 * @param root the run root
	 * @param timeout the wall-clock limit
	 * @param maxOutputChars the per-stream allowance
	 * @param arguments the tool-call arguments as the model sent them
	 * @return the tool result
	 */
	static Tool.Result call(RunRoot root, Duration timeout, int maxOutputChars, String arguments) {
		String command = text(argument(arguments, COMMAND));
		if (command.isBlank()) {
			return Tool.Result.error(RUN_COMMAND + " needs the '" + COMMAND + "' argument: the command line to run.");
		}
		String named = text(argument(arguments, WORKING_DIRECTORY));
		final Path directory;
		try {
			directory = root.resolve(named);
		}
		catch (RunRoot.EscapesRootException ex) {
			return Tool.Result.error(ex.getMessage());
		}
		return Tool.Result.text(render(run(command, directory, timeout, maxOutputChars), root, named));
	}

	/**
	 * Run one command, bounded. The two streams are read on their own threads so a
	 * command that fills a pipe cannot stop the wall-clock limit from being enforced, and
	 * a command that outlives it has its whole process tree killed rather than only its
	 * shell.
	 * @param command the command line
	 * @param directory the working directory, already confined
	 * @param timeout the wall-clock limit
	 * @param maxOutputChars the per-stream allowance
	 * @return the exit code, whether it timed out, and the captured output
	 */
	public static Outcome run(String command, Path directory, Duration timeout, int maxOutputChars) {
		ProcessBuilder builder = new ProcessBuilder(List.of("bash", "-lc", command)).directory(directory.toFile());
		try {
			Process process = builder.start();
			AtomicReference<Captured> stdout = new AtomicReference<>(Captured.EMPTY);
			AtomicReference<Captured> stderr = new AtomicReference<>(Captured.EMPTY);
			Thread out = reading(process.getInputStream(), stdout, maxOutputChars);
			Thread err = reading(process.getErrorStream(), stderr, maxOutputChars);
			boolean withinLimit = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
			int exitCode = -1;
			if (withinLimit) {
				exitCode = process.exitValue();
			}
			else {
				killTree(process);
				// Whether the kill landed is a separate question from whether the limit
				// was reached: a command the limit ended is reported as timed out even if
				// its corpse took the grace period to reap, and a tree that survived the
				// kill is still reported as timed out, with no exit code to read.
				boolean reaped = process.waitFor(KILL_GRACE.toMillis(), TimeUnit.MILLISECONDS);
				exitCode = reaped ? process.exitValue() : -1;
			}
			joined(out);
			joined(err);
			return new Outcome(exitCode, !withinLimit, stdout.get(), stderr.get());
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to start '" + command + "' in " + directory, ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return new Outcome(-1, true, Captured.EMPTY,
					new Captured("interrupted while waiting for the command: " + ex.getMessage(), 0, false));
		}
	}

	/**
	 * The answer as the model reads it: the verdict first, then the two streams, each
	 * with its truncation stated on the line that carries it.
	 * @param outcome what the command produced
	 * @param root the run root, quoted so a reader sees where the command ran
	 * @param named the working directory as the caller named it, blank when it named none
	 * @return the result text
	 */
	static String render(Outcome outcome, RunRoot root, String named) {
		StringBuilder text = new StringBuilder();
		text.append("exit code: ").append(outcome.exitCode()).append('\n');
		text.append("timed out: ").append(outcome.timedOut() ? "yes, killed at the limit" : "no").append('\n');
		text.append("working directory: ")
			.append(named.isBlank() ? root + " (the run root)" : root + " / " + named)
			.append('\n');
		text.append("stdout:\n").append(stream(outcome.stdout(), "stdout"));
		text.append("stderr:\n").append(stream(outcome.stderr(), "stderr"));
		return text.toString();
	}

	private static String stream(Captured captured, String name) {
		String kept = captured.text();
		if (!captured.truncated()) {
			return kept + "\n";
		}
		return kept + "\n[" + name + " truncated: kept " + kept.length() + " of " + captured.totalChars()
				+ " characters, dropped " + (captured.totalChars() - kept.length()) + "]\n";
	}

	/**
	 * Kill the shell and everything it spawned. {@code bash -lc} execs a simple command
	 * into its own process, but a compound one leaves children behind, and a surviving
	 * {@code sleep} holding the output pipes would hold the readers with it.
	 * @param process the process that outlived its limit
	 */
	private static void killTree(Process process) {
		process.descendants().forEach(ProcessHandle::destroyForcibly);
		process.destroyForcibly();
	}

	private static Thread reading(InputStream stream, AtomicReference<Captured> sink, int maxOutputChars) {
		Thread reader = new Thread(() -> sink.compareAndSet(Captured.EMPTY, capture(stream, maxOutputChars)),
				"smith-tools-coding-reader");
		reader.setDaemon(true);
		reader.start();
		return reader;
	}

	private static void joined(Thread reader) {
		try {
			reader.join(READ_GRACE.toMillis());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Read a stream up to the allowance, then keep reading to count what was dropped. The
	 * count is what makes the truncation a statement rather than a shortened answer, so
	 * it is worth draining for.
	 * @param stream the stream to read
	 * @param maxOutputChars how many characters to keep
	 * @return the kept text, the total, and whether it was cut
	 */
	static Captured capture(InputStream stream, int maxOutputChars) {
		StringBuilder kept = new StringBuilder();
		int total = 0;
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			char[] buffer = new char[8192];
			int read;
			while ((read = reader.read(buffer)) != -1) {
				total += read;
				if (kept.length() < maxOutputChars) {
					int room = Math.max(0, maxOutputChars - kept.length());
					kept.append(buffer, 0, Math.min(read, room));
				}
			}
		}
		catch (IOException ex) {
			String failed = "[failed to read the stream: " + ex.getMessage() + "]";
			return new Captured((kept.length() != 0) ? kept.toString() : failed, total, kept.length() < total);
		}
		return new Captured(kept.toString(), total, kept.length() < total);
	}

	private static JsonNode argument(String arguments, String name) {
		if (arguments == null || arguments.isBlank()) {
			return null;
		}
		try {
			JsonNode root = JSON.readTree(arguments);
			return root.isObject() ? root.get(name) : null;
		}
		catch (RuntimeException ex) {
			return null;
		}
	}

	private static String text(JsonNode node) {
		if (node == null || node.isNull() || !node.isValueNode()) {
			return "";
		}
		return node.asString();
	}

	/**
	 * What one command produced.
	 *
	 * @param exitCode the process exit code, or -1 when there was none to read — a
	 * command that had to be killed and did not, or one that never started
	 * @param timedOut whether the wall-clock limit ended it
	 * @param stdout captured standard output
	 * @param stderr captured standard error
	 */
	public record Outcome(int exitCode, boolean timedOut, Captured stdout, Captured stderr) {
	}

	/**
	 * One captured stream.
	 *
	 * @param text the output kept, at most the allowance of it
	 * @param totalChars the characters the command actually wrote, counted even past the
	 * point where keeping more stopped
	 * @param truncated whether the allowance cut any of it
	 */
	public record Captured(String text, int totalChars, boolean truncated) {

		/** A stream from which nothing was read. */
		static final Captured EMPTY = new Captured("", 0, false);

	}

}
