/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Minimal external-CLI runner for the shell-out scanners (gitleaks, skillspector).
 *
 * @author Ivan Rodriguez
 */
public final class ToolRunner {

	private ToolRunner() {
	}

	/**
	 * Whether the named executable can be found on {@code PATH}.
	 * @param executable bare executable name, without a directory
	 * @return {@code true} when some {@code PATH} directory holds it as an executable
	 * file
	 */
	public static boolean isAvailable(String executable) {
		String pathEnv = System.getenv("PATH");
		if (pathEnv == null) {
			return false;
		}
		for (String dir : pathEnv.split(java.io.File.pathSeparator)) {
			if (java.nio.file.Files.isExecutable(java.nio.file.Path.of(dir, executable))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Run {@code command} with {@code cwd}, capturing stdout/stderr, killing the process
	 * after {@code timeoutSeconds}.
	 * @param command the executable followed by its arguments
	 * @param cwd working directory for the process, or {@code null} to inherit this one
	 * @param timeoutSeconds how long to wait before killing the process
	 * @return the exit code with both captured streams; a timeout reports {@code -1}, an
	 * unrunnable command {@code 127}, and an interrupted or stream-failed run
	 * {@code 124}, each with the reason on stderr
	 */
	public static ToolResult run(List<String> command, Path cwd, long timeoutSeconds) {
		ProcessBuilder builder = new ProcessBuilder(command);
		if (cwd != null) {
			builder.directory(cwd.toFile());
		}
		Process process = null;
		try {
			process = builder.start();
			Process running = process;
			CompletableFuture<String> out = readAsync(running.getInputStream());
			CompletableFuture<String> err = readAsync(running.getErrorStream());
			if (!running.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
				running.destroyForcibly();
				return new ToolResult(-1, "", "timed out after " + timeoutSeconds + "s");
			}
			return new ToolResult(running.exitValue(), out.get(10, TimeUnit.SECONDS), err.get(10, TimeUnit.SECONDS));
		}
		catch (IOException ex) {
			return new ToolResult(127, "", (ex.getMessage() == null) ? ex.toString() : ex.getMessage());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			if (process != null) {
				process.destroyForcibly();
			}
			return new ToolResult(124, "", "interrupted");
		}
		catch (ExecutionException | TimeoutException ex) {
			if (process != null) {
				process.destroyForcibly();
			}
			return new ToolResult(124, "", "stream read failed: " + ex);
		}
	}

	private static CompletableFuture<String> readAsync(InputStream stream) {
		return CompletableFuture.supplyAsync(() -> {
			try (stream) {
				return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
			}
			catch (IOException ex) {
				return "";
			}
		});
	}

	/**
	 * Outcome of one CLI invocation.
	 *
	 * @param exitCode the process exit code, or the runner's own code when the process
	 * never reported one
	 * @param stdout captured standard output
	 * @param stderr captured standard error, which also carries the reason for a
	 * synthetic exit code
	 * @author Ivan Rodriguez
	 */
	public record ToolResult(int exitCode, String stdout, String stderr) {
		public boolean isSuccess() {
			return this.exitCode == 0;
		}
	}

}
