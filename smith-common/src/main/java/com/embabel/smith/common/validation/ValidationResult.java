/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.common.validation;

import java.util.List;

/**
 * The outcome of validating a agent spec: the full list of findings. An empty list means
 * the pack is well-formed and eligible to be written / ingested.
 *
 * @param issues the findings of the validation
 * @author Ivan Rodriguez
 */
public record ValidationResult(List<ValidationIssue> issues) {

	public ValidationResult {
		issues = (issues != null) ? List.copyOf(issues) : List.of();
	}

	public boolean isValid() {
		return this.issues.isEmpty();
	}

	public List<ValidationIssue> issues() {
		return this.issues;
	}

	public List<ValidationIssue> issuesWithCode(String code) {
		return this.issues.stream().filter((i) -> i.code().equals(code)).toList();
	}
}
