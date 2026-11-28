/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.common.validation;

/**
 * A single validation finding.
 *
 * @param code a stable, machine-readable category (e.g. {@code DANGLING_TYPE_REF})
 * @param message a human-readable explanation; for reference errors it names the
 * offending value
 * @param location the file or step the issue was found in, or {@code null} if pack-wide
 * @param severity whether the finding stops the pack being deployable, or only says
 * something about it. {@link Severity#ADVISORY} exists because some facts a reader needs
 * are true of a pack that is nonetheless valid — a declaration the runtime will ignore is
 * worth stating, and refusing the pack for it would be wrong.
 * @author Ivan Rodriguez
 */
public record ValidationIssue(String code, String message, String location, Severity severity) {

	/**
	 * Whether a finding makes a pack undeployable, or only reports something about it.
	 */
	public enum Severity {

		/** The pack is not deployable while this issue stands. */
		ERROR,
		/**
		 * The pack deploys unchanged; this is a statement about it that a reader needs.
		 */
		ADVISORY

	}

	public ValidationIssue {
		severity = (severity != null) ? severity : Severity.ERROR;
	}

	/** An error-severity finding, which is what every refusal is. */
	public ValidationIssue(String code, String message, String location) {
		this(code, message, location, Severity.ERROR);
	}

	public ValidationIssue(String code, String message) {
		this(code, message, null, Severity.ERROR);
	}

	public static ValidationIssue of(String code, String message) {
		return new ValidationIssue(code, message, null, Severity.ERROR);
	}

	public static ValidationIssue of(String code, String message, String location) {
		return new ValidationIssue(code, message, location, Severity.ERROR);
	}

	/**
	 * A finding that reports without refusing: the pack stays deployable, and the
	 * statement is there for the run report and for a reader asking why something did not
	 * happen.
	 * @param code the issue code
	 * @param message the explanation, naming the offending value
	 * @param location the file it was found in
	 * @return the advisory finding
	 */
	public static ValidationIssue advisory(String code, String message, String location) {
		return new ValidationIssue(code, message, location, Severity.ADVISORY);
	}

	/**
	 * Whether this finding stops the pack being deployable.
	 * @return true unless the finding is advisory only
	 */
	public boolean isError() {
		return this.severity == Severity.ERROR;
	}
}
