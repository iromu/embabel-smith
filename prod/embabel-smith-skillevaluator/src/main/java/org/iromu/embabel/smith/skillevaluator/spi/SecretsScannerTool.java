/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.spi;

import java.nio.file.Path;
import java.util.List;

/**
 * Seam for the external secrets-scanner CLI (Gitleaks), mirroring the shape of
 * {@code CodeRiskValidator.ScannerTool} so a test can drive the port without the binary
 * installed.
 * <p>
 * Unlike the code-risk tools, Gitleaks is invoked with a relative {@code --source} plus a
 * working directory (upstream keeps finding paths relative to the scan root), so
 * {@link #run(List, Path)} takes a {@code cwd}.
 *
 * @author Ivan Rodriguez
 */
public interface SecretsScannerTool {

	/**
	 * Human-readable tool name as it appears in user-facing messages (Python
	 * {@code ExternalTool.name}).
	 * @return the tool's display name, as user-facing messages state it
	 */
	String name();

	/**
	 * Hint shown when the executable cannot be resolved.
	 * @return the instructions to give the user so they can install the tool
	 */
	String installHint();

	/**
	 * Whether the executable can be resolved at all.
	 * @return {@code true} when the executable is present
	 */
	boolean isAvailable();

	/**
	 * Version string reported by the tool, or null.
	 * @return the version the tool reported, or {@code null} when it could not be read
	 */
	String version();

	/**
	 * Invoke the tool with these arguments, running in {@code cwd} when non-null.
	 * @param args command-line arguments following the executable itself
	 * @param cwd working directory for the process, or {@code null} to inherit this one
	 * @return the invocation outcome: exit code, captured streams and any failure to run
	 */
	RunResult run(List<String> args, Path cwd);

	/**
	 * Outcome of one invocation; mirrors the Python {@code ToolResult}.
	 *
	 * @param success whether the tool ran and reported an exit code of its own
	 * @param stdout captured standard output
	 * @param stderr captured standard error
	 * @param exitCode the tool's exit code, or {@code -1} when the run never produced one
	 * @param errorMessage why the run could not produce a result, {@code null} when it
	 * succeeded
	 * @author Ivan Rodriguez
	 */
	record RunResult(boolean success, String stdout, String stderr, int exitCode, String errorMessage) {
	}

}
