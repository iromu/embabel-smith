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
 *
 * <p>
 * Unlike the code-risk tools, Gitleaks is invoked with a relative {@code --source} plus a
 * working directory (upstream keeps finding paths relative to the scan root), so
 * {@link #run(List, Path)} takes a {@code cwd}.
 */
public interface SecretsScannerTool {

	/** Outcome of one invocation; mirrors the Python {@code ToolResult}. */
	record RunResult(boolean success, String stdout, String stderr, int exitCode, String errorMessage) {
	}

	/**
	 * Human-readable tool name as it appears in user-facing messages (Python
	 * {@code ExternalTool.name}).
	 */
	String name();

	/** Hint shown when the executable cannot be resolved. */
	String installHint();

	/** Whether the executable can be resolved at all. */
	boolean isAvailable();

	/** Version string reported by the tool, or null. */
	String version();

	/** Invoke the tool with these arguments, running in {@code cwd} when non-null. */
	RunResult run(List<String> args, Path cwd);

}
