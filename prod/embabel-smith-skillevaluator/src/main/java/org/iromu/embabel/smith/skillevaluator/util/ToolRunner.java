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

/** Minimal external-CLI runner for the shell-out scanners (gitleaks, skillspector). */
public final class ToolRunner {

	private ToolRunner() {
	}

	/** Outcome of one CLI invocation. */
	public record ToolResult(int exitCode, String stdout, String stderr) {
		public boolean isSuccess() {
			return exitCode == 0;
		}
	}

	/** Whether the named executable can be found on {@code PATH}. */
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
		catch (IOException e) {
			return new ToolResult(127, "", e.getMessage() == null ? e.toString() : e.getMessage());
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			if (process != null) {
				process.destroyForcibly();
			}
			return new ToolResult(124, "", "interrupted");
		}
		catch (ExecutionException | TimeoutException e) {
			if (process != null) {
				process.destroyForcibly();
			}
			return new ToolResult(124, "", "stream read failed: " + e);
		}
	}

	private static CompletableFuture<String> readAsync(InputStream stream) {
		return CompletableFuture.supplyAsync(() -> {
			try (stream) {
				return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
			}
			catch (IOException e) {
				return "";
			}
		});
	}

}
