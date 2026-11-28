/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.core;

import com.embabel.agent.core.AgentProcess;

/**
 * The outcome of a run: the process that was executed (or {@code null} when the run never
 * started, e.g. blocked by the trust tier), whether the goal was achieved, its output,
 * and the failure reason. {@code plannerName} is the planner the run resolved to
 * ({@code null} when the run never started); {@code plannerChoice} is that planner
 * together with the setting that named it, which is what lets the run's report say
 * whether the run's flag won or the pack's own default did.
 *
 * @param process the executed process, or {@code null} when the run never started
 * @param success whether the goal was achieved
 * @param output the goal output on success
 * @param error the failure reason
 * @param plannerName the planner the run resolved to
 * @param plannerChoice that planner and the setting that won, or {@code null} when the
 * run never resolved one
 * @author Ivan Rodriguez
 */
public record RunOutcome(AgentProcess process, boolean success, String output, String error, String plannerName,
		PlannerResolver.Decision plannerChoice) {

	/**
	 * A run that failed before a process was created (tier block, lifecycle block, goal
	 * not found).
	 */
	public static RunOutcome failed(String error) {
		return new RunOutcome(null, false, null, error, null, null);
	}
}
