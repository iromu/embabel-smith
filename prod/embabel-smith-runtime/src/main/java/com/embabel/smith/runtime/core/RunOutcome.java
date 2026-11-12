/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.embabel.smith.runtime.core;

import com.embabel.agent.core.AgentProcess;

/**
 * The outcome of a run: the process that was executed (or {@code null} when the run never
 * started, e.g. blocked by the trust tier), whether the goal was achieved, its output,
 * and the failure reason. {@code plannerName} is the planner the run resolved to
 * ({@code null} when the run never started).
 *
 * @param process the executed process, or {@code null} when the run never started
 * @param success whether the goal was achieved
 * @param output the goal output on success
 * @param error the failure reason
 * @param plannerName the planner the run resolved to
 * @author Ivan Rodriguez
 */
public record RunOutcome(AgentProcess process, boolean success, String output, String error, String plannerName) {

	/** A run that failed before a process was created (tier block, goal not found). */
	public static RunOutcome failed(String error) {
		return new RunOutcome(null, false, null, error, null);
	}
}
