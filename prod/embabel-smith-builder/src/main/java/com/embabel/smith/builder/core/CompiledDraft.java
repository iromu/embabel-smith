/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.core;

import java.util.List;

import com.embabel.smith.common.model.AgentSpec;

/**
 * The output of the {@code compileFromDescription} step: a draft spec (a
 * {@link AgentSpec}) produced by the natural-language compiler, together with any issues
 * the compiler surfaced for review. A draft with issues is still usable but must be
 * reviewed before it is trusted.
 *
 * @param agentName The name derived from the description.
 * @param pack The compiled draft agent spec.
 * @param issues The issues the compiler surfaced for review.
 * @author Ivan Rodriguez
 */
public record CompiledDraft(String agentName, AgentSpec pack, List<String> issues) {

	public CompiledDraft {
		issues = (issues != null) ? List.copyOf(issues) : List.of();
	}

	public boolean hasIssues() {
		return !this.issues.isEmpty();
	}
}
