/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.core;

import java.util.List;

import com.embabel.smith.common.model.ProcessSpec;

/**
 * The output of the multi-agent generation step: a draft {@link ProcessSpec} — the
 * build's second candidate beside the single-agent {@link CompiledDraft} — plus any
 * issues the generator surfaced for review.
 *
 * @param processName The name of the drafted process.
 * @param process The drafted process, its agents in run order.
 * @param issues The issues the generator surfaced for review.
 * @author Ivan Rodriguez
 */
public record ProcessDraft(String processName, ProcessSpec process, List<String> issues) {

	public ProcessDraft {
		issues = (issues != null) ? List.copyOf(issues) : List.of();
	}

	public boolean hasIssues() {
		return !this.issues.isEmpty();
	}
}
