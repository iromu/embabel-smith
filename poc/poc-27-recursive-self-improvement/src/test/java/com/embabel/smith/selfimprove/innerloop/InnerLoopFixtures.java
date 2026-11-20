/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.innerloop;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.embabel.agent.skills.script.SkillScriptExecutionEngine;
import com.embabel.smith.runtime.trace.TraceRecord;
import com.embabel.smith.skillscripts.GraalJsSkillScriptExecutionEngine;

/**
 * Test helpers for the inner loop: the corpus splits, a policy run, and synthetic cases
 * for the two triggers the corpus records on a representative line rather than on every
 * line.
 *
 * @author Ivan Rodriguez
 */
final class InnerLoopFixtures {

	/**
	 * One engine for the whole suite, with POC 26's defaults. The engine builds a fresh
	 * hardened context per execute, so sharing it costs nothing and keeps the runs under
	 * the same limits the generated policies will face.
	 */
	static final SkillScriptExecutionEngine ENGINE = new GraalJsSkillScriptExecutionEngine();

	/**
	 * A prompt that is wrong in both directions at once: it excludes the tier block by
	 * keyword, which loses the decoys, and it drops the lifecycle exclusion and the
	 * below-baseline trigger, which invents a defect and misses a real one. Scoring it is
	 * what shows two numbers rather than one.
	 */
	static final String MIXED_PROMPT = """
			POLICY b27-mixed
			TRIGGER failed_step WHEN failed UNLESS error_contains "trust tier"
			TRIGGER content_drift WHEN hash_differs
			""";

	private InnerLoopFixtures() {
	}

	/**
	 * @return the split policies are fitted against.
	 */
	static List<TraceCase> tuning() {
		return LabeledTraceReader.read(LabeledTraceReader.TUNING);
	}

	/**
	 * @return the split policies are judged on.
	 */
	static List<TraceCase> heldout() {
		return LabeledTraceReader.read(LabeledTraceReader.HELDOUT);
	}

	/**
	 * @return both splits, tuning first.
	 */
	static List<TraceCase> bothSplits() {
		List<TraceCase> all = new ArrayList<>(tuning());
		all.addAll(heldout());
		return List.copyOf(all);
	}

	/**
	 * Run a policy over a split.
	 * @param policy the policy
	 * @param cases the lines
	 * @param workDir where to write the skill folder
	 * @return the run.
	 */
	static PolicyRun run(DetectionPolicy policy, List<TraceCase> cases, Path workDir) {
		return new PolicyRunner(ENGINE).run(policy, cases, workDir);
	}

	/**
	 * Index a run's verdicts by the line they answer.
	 * @param run a run that produced verdicts
	 * @return the verdicts by run id.
	 */
	static Map<String, PolicyRun.Verdict> byRunId(PolicyRun run) {
		Map<String, PolicyRun.Verdict> byRunId = new HashMap<>();
		for (PolicyRun.Verdict verdict : run.verdicts()) {
			byRunId.put(verdict.runId(), verdict);
		}
		return byRunId;
	}

	/**
	 * The lines a policy flagged as defects.
	 * @param run a run that produced verdicts
	 * @return the flagged run ids.
	 */
	static Set<String> flagged(PolicyRun run) {
		Set<String> flagged = new HashSet<>();
		for (PolicyRun.Verdict verdict : run.verdicts()) {
			if (verdict.defect()) {
				flagged.add(verdict.runId());
			}
		}
		return flagged;
	}

	/**
	 * A successful run whose pack is being re-evaluated against a recorded baseline.
	 * Drift is off here — the pack hash equals the sidecar's — so the below-baseline
	 * trigger is the only one that can fire, which is what makes the case a control
	 * rather than a restatement.
	 * @param runId the synthetic line's id
	 * @param baseline the sidecar's recorded pass-rate
	 * @param reeval the re-evaluated pass-rate
	 * @return the case.
	 */
	static TraceCase reevalCase(String runId, double baseline, double reeval) {
		TraceCase.SpecContext context = new TraceCase.SpecContext("sha256:aaaa", baseline, reeval);
		return new TraceCase(runId, record(runId, true, null, "sha256:aaaa"), TraceCase.NOT_DEFECT, "success", context);
	}

	/**
	 * A successful run whose pack hash may or may not match its sidecar. Nothing else can
	 * fire, so this isolates the drift trigger.
	 * @param runId the synthetic line's id
	 * @param packHash the hash the pack has now
	 * @param sidecarHash the hash the sidecar recorded
	 * @param label the human answer to assert against
	 * @return the case.
	 */
	static TraceCase driftCase(String runId, String packHash, String sidecarHash, String label) {
		TraceCase.SpecContext context = new TraceCase.SpecContext(sidecarHash, 1.0, null);
		return new TraceCase(runId, record(runId, true, null, packHash), label, "content_drift", context);
	}

	private static TraceRecord record(String runId, boolean success, String error, String packHash) {
		return new TraceRecord(runId, null, new TraceRecord.SourceRef("folder:/synthetic", null, null),
				new TraceRecord.PackRef("synthetic-pack", packHash), "syntheticGoal", "synthetic input", "GOAP",
				"standard", List.of(), success, error, null);
	}

}
