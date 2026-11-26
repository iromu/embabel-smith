/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.security;

/**
 * A single security finding from scanning a agent spec: how serious it is, what class of
 * risk it is, a human-readable message, and the concrete evidence (the offending text or
 * tool group).
 *
 * @param severity the severity of the finding.
 * @param category the class of risk, one of the {@code CAT_*} constants.
 * @param message a human-readable description of the finding.
 * @param evidence the concrete evidence, such as the offending text or tool group.
 * @author Ivan Rodriguez
 */
public record Finding(Severity severity, String category, String message, String evidence) {

	/**
	 * Prompt-injection text in the spec's own prompt (doc 02 §5.3 — the spec as an attack
	 * vector).
	 */
	public static final String CAT_PROMPT_INJECTION = "PROMPT_INJECTION";

	/** The spec declares a high-privilege tool group (e.g. {@code shell}). */
	public static final String CAT_HIGH_PRIVILEGE_TOOL = "HIGH_PRIVILEGE_TOOL";

	/** The spec's prompt reaches an external network source (URL / fetch command). */
	public static final String CAT_NETWORK_REFERENCE = "NETWORK_REFERENCE";

	/**
	 * The spec's prompt asks to read or exfiltrate secrets (API keys, tokens, dotfiles).
	 */
	public static final String CAT_SECRET_EXFIL = "SECRET_EXFIL";

	/**
	 * Untrusted user input reaches a {@code shell} tool group (the injection-to-execution
	 * chain).
	 */
	public static final String CAT_UNTRUSTED_INPUT_TO_SHELL = "UNTRUSTED_INPUT_TO_SHELL";

	/**
	 * A skill script reaches for the host: its module loader, its class loader, a
	 * subprocess, or generated code. The script sandbox denies every one of these, so a
	 * script that asks for them is a script written outside its contract.
	 */
	public static final String CAT_SCRIPT_HOST_ACCESS = "SCRIPT_HOST_ACCESS";

	/**
	 * A skill script reaches the network through an API (fetch, XHR, a socket). The
	 * engine gives a script no network at all, so this is both a broken promise to the
	 * reader and an attempt to leave the sandbox by another door.
	 */
	public static final String CAT_SCRIPT_NETWORK = "SCRIPT_NETWORK";

	/**
	 * A skill script names a path that walks out of the skill's own folder. The engine
	 * binds file access to that folder, so a script reaching above it is reaching for
	 * something outside the artifact.
	 */
	public static final String CAT_SCRIPT_PATH_ESCAPE = "SCRIPT_PATH_ESCAPE";

	public boolean isCritical() {
		return this.severity == Severity.CRITICAL;
	}

	public boolean isWarning() {
		return this.severity == Severity.WARN;
	}
}
