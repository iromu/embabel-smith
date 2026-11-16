/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.unified.dto;

import java.util.List;

/**
 * The request for the eval/refinement pane: which spec to evaluate, and (optionally) the
 * eval tasks to run against it. When no tasks are supplied, {@link #tasksOrDefault()}
 * falls back to a small default set.
 *
 * @param specName The name of the agent spec to evaluate.
 * @param tasks The eval tasks to run, or null to use the defaults.
 * @author Ivan Rodriguez
 */
public record EvalRequest(String specName, List<EvalTask> tasks) {

	public List<EvalTask> tasksOrDefault() {
		return (this.tasks == null || this.tasks.isEmpty()) ? EvalTask.DEFAULTS : this.tasks;
	}
}
