/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.adapter;

import java.util.List;

import com.embabel.agent.api.tool.Tool;

/**
 * The real shell executor behind the {@code shell} tool group. Runs a command via
 * {@link ProcessBuilder} and returns its exit code, stdout and stderr. This is the
 * "bridge" the agent spec's {@code toolGroups: [shell]} action gets injected (doc 03
 * §2.3, Option A — the shell tool group, the simplest of Embabel's three CLI bridges).
 *
 * @author Ivan Rodriguez
 */
public final class ShellCommandTool {

	/** Utility class — static API only. */
	private ShellCommandTool() {
	}

	/** The tool name the spec's {@code shell} action binds to. */
	public static final String NAME = "runCommand";

	public static Tool tool() {
		return Tool.fromFunction(NAME, "Run a shell command on the host and return its exit code, stdout and stderr.",
				CommandRequest.class, CommandResult.class, ShellCommandTool::run);
	}

	/**
	 * Pure, offline-testable: execute {@code request.command()} and capture the outcome.
	 * @param request the command to run.
	 * @return the exit code, stdout and stderr of the run.
	 */
	public static CommandResult run(CommandRequest request) {
		try {
			ProcessBuilder builder = new ProcessBuilder(List.of("bash", "-lc", request.command()));
			Process process = builder.start();
			String stdout = new String(process.getInputStream().readAllBytes());
			String stderr = new String(process.getErrorStream().readAllBytes());
			int exitCode = process.waitFor();
			return new CommandResult(exitCode, stdout, stderr);
		}
		catch (Exception ex) {
			return new CommandResult(-1, "", ex.toString());
		}
	}

	public record CommandRequest(String command) {
	}

	public record CommandResult(int exitCode, String stdout, String stderr) {
	}

}
