/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.spi;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.iromu.embabel.smith.skillevaluator.util.ToolRunner;

/**
 * Default {@link SecretsScannerTool}: the Gitleaks CLI.
 * <p>
 * Executable resolution, version probing and error text follow the Python
 * {@code ExternalTool} that backs {@code Tools.gitleaks}. Two documented deviations from
 * that Python wrapper:
 * <ul>
 * <li><b>Managed provisioning.</b> Upstream can download a pinned Gitleaks binary when
 * none is on {@code PATH} ({@code skillevaluator.utils.scanner_install}). This port has
 * no provisioning layer, so a missing binary simply reports unavailable and the scan is
 * marked incomplete — the same end state the Python port reaches when provisioning is
 * unavailable.</li>
 * <li><b>No interpreter-sibling or env override.</b> Python's Gitleaks entry takes no
 * {@code override_env} and uses plain {@code PATH} precedence, so nothing is lost by
 * resolving through {@link ToolRunner#isAvailable}.</li>
 * <li><b>Report-to-stdout support is version dependent.</b> The upstream argv asks for
 * the JSON report on stdout with {@code --report-path -}. Gitleaks 8.16.0 (the Ubuntu
 * package installed here) does not honour that: it writes a file literally named
 * {@code -} into the working directory, stdout stays empty, and the scan ends up
 * "Gitleaks findings exit did not return a valid nonempty JSON report" →
 * {@code incomplete}. The Python upstream behaves the same way with the same binary, so
 * the argv was left untouched rather than adapted.</li>
 * </ul>
 *
 * @author Ivan Rodriguez
 */
public final class SecretsGitleaksCli implements SecretsScannerTool {

	/** Executable name looked up on {@code PATH}. */
	public static final String COMMAND = "gitleaks";

	/** Python {@code ExternalTool.run(..., timeout=120)} for the secrets scan. */
	public static final long TIMEOUT_SECONDS = 120;

	/** Version probe timeout (Python {@code ExternalTool.version} uses 30s). */
	private static final long VERSION_TIMEOUT_SECONDS = 30;

	private static final Pattern VERSION_PATTERN = Pattern.compile("\\d+\\.\\d+(?:\\.\\d+)?");

	/** Resolved executable, or null when unavailable. */
	private final String path;

	private String cachedVersion;

	public SecretsGitleaksCli() {
		this.path = ToolRunner.isAvailable(COMMAND) ? COMMAND : null;
	}

	@Override
	public String name() {
		return "Gitleaks";
	}

	/**
	 * Verbatim upstream hint (Gitleaks is a Go binary, so the text is still accurate for
	 * a Java host).
	 */
	@Override
	public String installHint() {
		return "Install with: brew install gitleaks (macOS) or go install github.com/gitleaks/gitleaks/v8@latest";
	}

	@Override
	public boolean isAvailable() {
		return this.path != null;
	}

	/**
	 * Python runs {@code gitleaks version} (subcommand, not a flag) and reads the first
	 * version-looking token.
	 */
	@Override
	public String version() {
		if (this.path == null) {
			return null;
		}
		if (this.cachedVersion == null) {
			ToolRunner.ToolResult probe = ToolRunner.run(List.of(this.path, "version"), null, VERSION_TIMEOUT_SECONDS);
			Matcher stdout = VERSION_PATTERN.matcher(probe.stdout());
			Matcher stderr = VERSION_PATTERN.matcher(probe.stderr());
			String found = stdout.find() ? stdout.group() : (stderr.find() ? stderr.group() : "");
			this.cachedVersion = found;
		}
		return this.cachedVersion.isEmpty() ? null : this.cachedVersion;
	}

	@Override
	public RunResult run(List<String> args, Path cwd) {
		if (!isAvailable()) {
			return new RunResult(false, "", "", -1, name() + " not installed. " + installHint());
		}
		List<String> cmd = new ArrayList<>();
		cmd.add(this.path);
		cmd.addAll(args);
		ToolRunner.ToolResult raw = ToolRunner.run(cmd, cwd, TIMEOUT_SECONDS);
		if (raw.exitCode() == -1) {
			return new RunResult(false, raw.stdout(), raw.stderr(), -1,
					name() + " timed out after " + TIMEOUT_SECONDS + " seconds");
		}
		if (raw.exitCode() == 127) {
			return new RunResult(false, "", "", -1, name() + " binary not found in PATH");
		}
		if (raw.exitCode() == 124) {
			return new RunResult(false, raw.stdout(), raw.stderr(), -1, name() + " execution failed: interrupted");
		}
		return new RunResult(true, raw.stdout(), raw.stderr(), raw.exitCode(), null);
	}

}
