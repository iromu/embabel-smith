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

package com.embabel.smith.runtime.trace;

import java.time.Instant;
import java.util.List;

/**
 * One trace record per run, appended to the JSONL trace store (schema per design.md).
 *
 * <p>
 * {@code source.ref} is the pinned ref of a git source ({@code null} for folder sources
 * or unpinned git sources); {@code source.resolvedAt} is the commit SHA the git source
 * materialized at ({@code null} for folder sources). {@code pack} names the pack that
 * exports the run's goal ({@code null} when the goal was not found). {@code steps} is
 * empty when the run never executed (tier block, goal not found, or no plan reachable).
 *
 * @param runId the unique run identifier
 * @param timestamp when the run was recorded
 * @param source the spec source reference (URI, pinned ref, resolved commit)
 * @param pack the pack that exports the run's goal, or {@code null}
 * @param goal the goal the run targeted
 * @param input the input bound to the goal's {@code UserInput}
 * @param planner the planner the run resolved to
 * @param trustTier the trust tier the run executed under
 * @param steps the executed steps, empty when the run never executed
 * @param success whether the goal was achieved
 * @param error the failure reason, or {@code null} on success
 * @param outputExcerpt an excerpt of the goal output
 * @author Ivan Rodriguez
 */
public record TraceRecord(String runId, Instant timestamp, SourceRef source, PackRef pack, String goal, String input,
		String planner, String trustTier, List<StepRecord> steps, boolean success, String error, String outputExcerpt) {

	public record SourceRef(String uri, String ref, String resolvedAt) {
	}

	public record PackRef(String name, String contentHash) {
	}

	/**
	 * One executed step: name, status ({@code completed}/{@code failed}), duration,
	 * output excerpt.
	 * @param name the action step name
	 * @param status {@code completed} or {@code failed}
	 * @param durationMs the step duration in milliseconds
	 * @param outputExcerpt an excerpt of the step's output
	 */
	public record StepRecord(String name, String status, long durationMs, String outputExcerpt) {
	}
}
