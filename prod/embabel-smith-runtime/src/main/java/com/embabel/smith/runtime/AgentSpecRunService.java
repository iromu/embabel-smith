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

package com.embabel.smith.runtime;

import java.nio.file.Path;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.smith.runtime.core.GoalNotFoundException;
import com.embabel.smith.runtime.core.IngestedSource;
import com.embabel.smith.runtime.core.RunOutcome;
import com.embabel.smith.runtime.core.RuntimeCore;
import com.embabel.smith.runtime.core.TrustTier;
import com.embabel.smith.runtime.core.TrustTierBlockedException;
import com.embabel.smith.runtime.trace.JsonlTraceStore;
import com.embabel.smith.runtime.trace.TraceRecord;
import com.embabel.smith.runtime.trace.TraceRecorder;
import com.embabel.smith.runtime.trace.TraceStore;
import com.embabel.smith.specsource.ResolvedSpecSource;
import com.embabel.smith.specsource.SpecSource;

/**
 * The product run service: source URI in, run outcome (plus its trace record) out.
 *
 * <p>
 * Plain class — no Spring — so the CLI and the studio (change 06) both embed it. Flow:
 * parse + resolve the spec source (change 02), ingest all packs (POC 1/6), run the goal
 * with planner selection and trust-tier enforcement (POC 1/10), and append the trace
 * record.
 *
 * @author Ivan Rodriguez
 */
public class AgentSpecRunService {

	private final RuntimeCore core;

	private final TraceRecorder recorder;

	/**
	 * Uses the default trace store ({@code ~/.smith/traces/runs.jsonl}).
	 * @param platform the booted agent platform
	 */
	public AgentSpecRunService(AgentPlatform platform) {
		this(platform, JsonlTraceStore.defaultPath());
	}

	/**
	 * The JSONL trace store at {@code traceFile} — the CLI {@code --trace-dir} path.
	 * @param platform the booted agent platform
	 * @param traceFile the JSONL trace file
	 */
	public AgentSpecRunService(AgentPlatform platform, Path traceFile) {
		this(platform, new JsonlTraceStore(traceFile));
	}

	/**
	 * Uses an alternative {@link TraceStore} (change: smith-studio-postgres-storage): the
	 * studio supplies a database-backed or file-backed store selected from its storage
	 * settings.
	 * @param platform the booted agent platform
	 * @param traceStore the trace store to append records to
	 */
	public AgentSpecRunService(AgentPlatform platform, TraceStore traceStore) {
		this.core = new RuntimeCore(platform);
		this.recorder = new TraceRecorder(traceStore);
	}

	/**
	 * Runs the named goal over the packs resolved from {@code sourceUri}. When
	 * {@code goal} is {@code null}, the single exported goal is used (an error lists the
	 * goals when there are several). A trace record is appended for every run attempt,
	 * success or failure.
	 * @param sourceUri the spec source URI ({@code folder:<path>} or {@code git:<url>[@ref]})
	 * @param goal the goal to run, or {@code null} for the single exported goal
	 * @param input the input bound to the goal's {@code UserInput}
	 * @param plannerOverride an explicit planner name, or {@code null} for the default
	 * @param tier the trust tier the run executes under
	 * @return the run outcome with its trace record
	 */
	public RunResult run(String sourceUri, String goal, String input, String plannerOverride, TrustTier tier) {
		String effectiveInput = (input != null) ? input : "";
		ResolvedSpecSource resolved = null;
		IngestedSource ingested = null;
		String effectiveGoal = goal;
		RunOutcome outcome;
		try {
			resolved = SpecSource.parse(sourceUri).resolve();
			ingested = this.core.ingest(resolved);
			effectiveGoal = (goal != null && !goal.isBlank()) ? goal : singleGoal(ingested);
			try {
				outcome = this.core.run(ingested, effectiveGoal, effectiveInput, plannerOverride, tier);
			}
			catch (GoalNotFoundException | TrustTierBlockedException ex) {
				outcome = RunOutcome.failed(ex.getMessage());
			}
		}
		catch (RuntimeException ex) {
			// resolution, ingestion, or goal selection failed before the run — still a
			// run attempt
			outcome = RunOutcome.failed(ex.getMessage());
		}

		TraceRecord record = safeRecord(sourceUri, resolved, ingested, effectiveGoal, effectiveInput, tier, outcome);
		return new RunResult(outcome.success(), outcome.output(), outcome.error(), record);
	}

	/**
	 * Records the run, returning {@code null} when the trace cannot be written — tracing
	 * observes, never intervenes: a trace-write failure must not change the run's result.
	 * @param sourceUri the spec source URI
	 * @param resolved the resolved source, or {@code null} when resolution failed
	 * @param ingested the ingested packs, or {@code null} when ingestion failed
	 * @param goal the goal the run targeted
	 * @param input the input bound to the goal's {@code UserInput}
	 * @param tier the trust tier the run executed under
	 * @param outcome the run outcome
	 * @return the appended trace record, or {@code null} when the trace could not be written
	 */
	private TraceRecord safeRecord(String sourceUri, ResolvedSpecSource resolved, IngestedSource ingested, String goal,
			String input, TrustTier tier, RunOutcome outcome) {
		try {
			return this.recorder.record(sourceUri, resolved, ingested, goal, input, tier, outcome);
		}
		catch (RuntimeException ex) {
			return null;
		}
	}

	private String singleGoal(IngestedSource ingested) {
		var goals = ingested.goalNames();
		if (goals.size() == 1) {
			return goals.get(0);
		}
		throw new IllegalStateException(
				"The source exports multiple goals: " + goals + ". Name one explicitly to run it.");
	}

	/**
	 * The outcome of a run: success, the goal output, the failure reason, and the emitted
	 * trace.
	 * @param success whether the goal was achieved
	 * @param output the goal output on success
	 * @param error the failure reason
	 * @param trace the emitted trace record
	 */
	public record RunResult(boolean success, String output, String error, TraceRecord trace) {
	}

}
