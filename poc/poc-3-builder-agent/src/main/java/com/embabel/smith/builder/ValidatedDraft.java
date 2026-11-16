/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder;

import java.util.List;

import com.embabel.smith.common.model.AgentSpec;

/**
 * The output of the {@code validateDraft} step: the draft plus whether its bindings and
 * schemas are consistent. When {@code valid} is false, the draft MUST NOT be written
 * (design D5) — the builder reports the issues instead.
 *
 * @param pack the draft agent spec that was validated
 * @param valid whether the draft's bindings and schemas are consistent
 * @param issues the validation issues, if any
 * @author Ivan Rodriguez
 */
public record ValidatedDraft(AgentSpec pack, boolean valid, List<String> issues) {

	public ValidatedDraft {
		issues = (issues != null) ? List.copyOf(issues) : List.of();
	}
}
