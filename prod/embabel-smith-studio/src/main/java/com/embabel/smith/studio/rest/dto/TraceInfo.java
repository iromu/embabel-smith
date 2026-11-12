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

package com.embabel.smith.studio.rest.dto;

import java.util.List;

import com.embabel.smith.runtime.trace.TraceRecord;

/**
 * One run's trace record (the Traces pane): run time, source ref, success/failure, the
 * per-step records (for step-level drill-down), and the failure reason where present.
 *
 * @param runId the run id.
 * @param timestamp the run timestamp (ISO-8601).
 * @param sourceUri the spec source URI.
 * @param sourceRef the spec source reference.
 * @param resolvedAt when the source ref was resolved (ISO-8601).
 * @param pack the pack name the run targeted.
 * @param goal the goal that was run.
 * @param input the input bound to the run.
 * @param planner the planner used (override or default).
 * @param trustTier the trust tier in effect.
 * @param steps the per-step trace records.
 * @param success whether the run succeeded.
 * @param error the failure reason where present.
 * @param outputExcerpt a short excerpt of the run output.
 * @author Ivan Rodriguez
 */
public record TraceInfo(String runId, String timestamp, String sourceUri, String sourceRef, String resolvedAt,
		String pack, String goal, String input, String planner, String trustTier, List<StepInfo> steps, boolean success,
		String error, String outputExcerpt) {

	public static TraceInfo of(TraceRecord record) {
		TraceRecord.SourceRef source = record.source();
		TraceRecord.PackRef pack = record.pack();
		List<StepInfo> steps = record.steps().stream().map(StepInfo::of).toList();
		return new TraceInfo(record.runId(), record.timestamp().toString(), (source != null) ? source.uri() : null,
				(source != null) ? source.ref() : null, (source != null) ? source.resolvedAt() : null,
				(pack != null) ? pack.name() : null, record.goal(), record.input(), record.planner(), record.trustTier(),
				steps, record.success(), record.error(), record.outputExcerpt());
	}
}
