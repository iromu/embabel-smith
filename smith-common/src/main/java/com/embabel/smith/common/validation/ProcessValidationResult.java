/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.common.validation;

import java.util.List;

/**
 * The outcome of validating a process: the findings, the cross-agent glue the process
 * actually has, and the cycle the repair has to break.
 *
 * <p>
 * An empty issue list means the process is sound and eligible to be published.
 *
 * @param issues the findings of the validation, empty when the process is valid
 * @param sharedTypes the number of types referenced by more than one agent — the
 * hand-offs that make it a process rather than a pile of packs
 * @param connected whether the process flows from {@code UserInput} to its final goal
 * @param cycle the agents of a circular dependency between agents, in dependency order,
 * empty when there is none
 * @author Ivan Rodriguez
 */
public record ProcessValidationResult(List<String> issues, int sharedTypes, boolean connected, List<String> cycle) {

	public ProcessValidationResult {
		issues = (issues != null) ? List.copyOf(issues) : List.of();
		cycle = (cycle != null) ? List.copyOf(cycle) : List.of();
	}

	public boolean isValid() {
		return this.issues.isEmpty();
	}

	/** The process has agents that depend on each other's outputs. */
	public boolean hasCycle() {
		return !this.cycle.isEmpty();
	}
}
