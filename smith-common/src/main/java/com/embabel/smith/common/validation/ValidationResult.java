/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.common.validation;

import java.util.List;

/**
 * The outcome of validating a agent spec: the full list of findings. An empty list means
 * the pack is well-formed and eligible to be written / ingested.
 * <p>
 * A finding carries a {@link ValidationIssue.Severity}: an {@code ERROR} stops the pack
 * being deployable, an {@code ADVISORY} only states something about it. So
 * {@link #isValid()} is the absence of errors, not the absence of findings — a pack can
 * be deployable and still have something worth saying about it, which is how a
 * declaration the runtime will ignore gets reported without refusing the pack.
 *
 * @param issues the findings of the validation, errors and advisories together
 * @author Ivan Rodriguez
 */
public record ValidationResult(List<ValidationIssue> issues) {

	public ValidationResult {
		issues = (issues != null) ? List.copyOf(issues) : List.of();
	}

	/**
	 * Whether the pack is deployable: true when nothing found rises to an error.
	 * @return true when no error-severity finding stands
	 */
	public boolean isValid() {
		return this.issues.stream().noneMatch(ValidationIssue::isError);
	}

	public List<ValidationIssue> issues() {
		return this.issues;
	}

	/**
	 * The findings that stop the pack being deployable.
	 * @return the error-severity findings
	 */
	public List<ValidationIssue> errors() {
		return this.issues.stream().filter(ValidationIssue::isError).toList();
	}

	/**
	 * The findings that report about a pack without refusing it.
	 * @return the advisory-severity findings
	 */
	public List<ValidationIssue> advisories() {
		return this.issues.stream().filter((i) -> !i.isError()).toList();
	}

	public List<ValidationIssue> issuesWithCode(String code) {
		return this.issues.stream().filter((i) -> i.code().equals(code)).toList();
	}
}
