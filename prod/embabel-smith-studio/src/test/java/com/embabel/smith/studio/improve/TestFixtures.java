/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.improve;

import com.embabel.smith.builder.improve.PendingCandidate;
import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.builder.lifecycle.LifecycleState;
import com.embabel.smith.builder.lifecycle.SpecContentHash;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.common.trace.TraceRecord;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Shared fixtures for the self-improvement-loop tests. */
final class TestFixtures {

	private TestFixtures() {
	}

	/**
	 * A valid pack: an exported goal and one action step per name, framework I/O types
	 * only, and a prompt that interpolates the {@code UserInput} each step declares — a
	 * step that names none of its declared inputs cannot reach the model and now fails
	 * validation.
	 */
	static AgentSpec pack(String name, String... stepNames) {
		return new AgentSpec(new AgentSpecManifest(name, "1.0.0"),
				GoalStepSpec.exportedGoal(name, "goal " + name, "BackgroundMessage"), steps(name, stepNames),
				List.of());
	}

	/** A valid pack whose single step carries a custom (possibly unsafe) prompt. */
	static AgentSpec packWithPrompt(String name, String stepName, String prompt) {
		List<ActionStepSpec> steps = List
			.of(new ActionStepSpec(ActionStepSpec.STEP_TYPE, stepName, "step " + stepName, List.of("UserInput"),
					"BackgroundMessage", prompt + " {{userInput.content}}", null, null, null, null, null, null, null));
		return new AgentSpec(new AgentSpecManifest(name, "1.0.0"),
				GoalStepSpec.exportedGoal(name, "goal " + name, "BackgroundMessage"), steps, List.of());
	}

	private static List<ActionStepSpec> steps(String name, String... stepNames) {
		List<ActionStepSpec> out = new ArrayList<>();
		for (String step : stepNames) {
			out.add(ActionStepSpec.action(step, "step " + step, List.of("UserInput"), "BackgroundMessage",
					"perform " + step + " carefully {{userInput.content}}"));
		}
		return out;
	}

	/**
	 * A sidecar for a pack at a given baseline and state (content hash computed from the
	 * pack).
	 */
	static LifecycleMetadata sidecar(AgentSpec pack, double baseline, LifecycleState state) {
		return new LifecycleMetadata(SpecContentHash.of(pack), baseline, state, Instant.now().toString());
	}

	/** A failed, non-tier-block trace for the named spec, with the given step records. */
	static TraceRecord failedTrace(String runId, String spec, String input, TraceRecord.StepRecord... steps) {
		return trace(runId, spec, input, false, "run failed", steps);
	}

	/** A successful trace for the named spec. */
	static TraceRecord okTrace(String runId, String spec, String input, TraceRecord.StepRecord... steps) {
		return trace(runId, spec, input, true, null, steps);
	}

	/** A trust-tier block trace (failed, no executed steps). */
	static TraceRecord tierBlockTrace(String runId, String spec, String input) {
		return trace(runId, spec, input, false, "Run blocked by trust tier: untrusted", new TraceRecord.StepRecord[0]);
	}

	/** A lifecycle block trace — the pack was retired (failed, no executed steps). */
	static TraceRecord lifecycleBlockTrace(String runId, String spec, String input) {
		return trace(runId, spec, input, false,
				"Run blocked by lifecycle state: goal 'x' is exported only by the retired pack '" + spec + "'",
				new TraceRecord.StepRecord[0]);
	}

	private static TraceRecord trace(String runId, String spec, String input, boolean success, String error,
			TraceRecord.StepRecord... steps) {
		return new TraceRecord(runId, Instant.now(), new TraceRecord.SourceRef("folder:/tmp", null, null),
				new TraceRecord.PackRef(spec, "hash"), spec, input, "GOAP", "trusted", List.of(steps), success, error,
				success ? "ok" : null);
	}

	static TraceRecord.StepRecord step(String name, String status, String outputExcerpt) {
		return new TraceRecord.StepRecord(name, status, 10, outputExcerpt);
	}

	/** A pending candidate for approval-queue tests. */
	static PendingCandidate candidate(String id, String spec) {
		AgentSpec pack = pack(spec, "s1");
		LifecycleMetadata proposed = sidecar(pack, 1.0, LifecycleState.ACTIVE);
		return new PendingCandidate(id, spec, pack, proposed, 1.0, 0.5, null, List.of(), "rewrote prompts of: s1");
	}

}
