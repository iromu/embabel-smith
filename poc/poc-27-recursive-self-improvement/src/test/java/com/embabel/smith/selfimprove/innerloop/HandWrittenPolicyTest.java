/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.innerloop;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.embabel.smith.builder.improve.Detector;
import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.builder.lifecycle.LifecycleState;
import com.embabel.smith.builder.lifecycle.SpecContentHash;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.trace.TraceRecord;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 4.1: a hand-written detection policy run through POC 26's engine reproduces the
 * three triggers the product's loop already uses.
 *
 * <p>
 * The agreement that matters is with the product's own code, not with a paraphrase of it,
 * so two of the three triggers are checked against {@code Detector} itself on the same
 * window — the failed-run trigger against {@code failedNonPolicyBlock}, the drift trigger
 * against {@code hasDrift} over a real {@code SpecContentHash}, the below-baseline
 * trigger against {@code belowBaseline}. The third leg is the corpus: every line's
 * verdict against the human label, and every defect flagged for the reason the label
 * gives rather than for any reason that happens to be true.
 *
 * @author Ivan Rodriguez
 */
class HandWrittenPolicyTest {

	@TempDir
	Path workDir;

	private PolicyRun run(List<TraceCase> cases) {
		return InnerLoopFixtures.run(DetectionPolicy.handWritten(), cases, this.workDir);
	}

	@Test
	void handWrittenPolicyAgreesWithEveryHeldOutLabel() {
		List<TraceCase> heldout = InnerLoopFixtures.heldout();
		PolicyRun run = run(heldout);

		assertThat(run.ran()).as("the hand-written policy must run: " + run.diagnostic()).isTrue();
		Map<String, PolicyRun.Verdict> verdicts = InnerLoopFixtures.byRunId(run);
		for (TraceCase line : heldout) {
			assertThat(verdicts.get(line.runId()).defect()).as("line " + line.runId() + " labelled " + line.label())
				.isEqualTo(line.labelledDefect());
		}
		SplitScore score = SplitScore.of(LabeledTraceReader.HELDOUT, heldout, run.verdicts());
		assertThat(score.precision()).isEqualTo(1.0);
		assertThat(score.recall()).isEqualTo(1.0);
	}

	@Test
	void handWrittenPolicyAgreesWithEveryTuningLabel() {
		List<TraceCase> tuning = InnerLoopFixtures.tuning();
		PolicyRun run = run(tuning);

		assertThat(run.ran()).as("the hand-written policy must run: " + run.diagnostic()).isTrue();
		Map<String, PolicyRun.Verdict> verdicts = InnerLoopFixtures.byRunId(run);
		for (TraceCase line : tuning) {
			assertThat(verdicts.get(line.runId()).defect()).as("line " + line.runId() + " labelled " + line.label())
				.isEqualTo(line.labelledDefect());
		}
	}

	@Test
	void policyBlocksAreNotDefectsAndTheDecoysThatMentionThemAre() {
		Set<String> flagged = InnerLoopFixtures.flagged(run(InnerLoopFixtures.bothSplits()));

		// The two policy blocks, matched by prefix exactly as Detector.java does:
		// refining a
		// prompt cannot fix either of them.
		assertThat(flagged).doesNotContain("t-002", "t-012", "h-002", "h-009", "t-006", "h-005");
		// t-007 and h-006 failed for a reason whose message mentions the trust tier
		// without
		// carrying the prefix, so the product's startsWith rule makes them defects. A
		// policy
		// that keyword-matched "trust tier" would call them policy blocks and lose them.
		assertThat(flagged).contains("t-007", "h-006");
	}

	@Test
	void failedRunTriggerAgreesWithTheProductDetector() {
		// Only lines with no spec_context, so the failed-run trigger is the only one that
		// can
		// fire and the comparison is one-to-one with the product's own filter.
		List<TraceCase> window = InnerLoopFixtures.bothSplits()
			.stream()
			.filter((line) -> line.specContext() == null)
			.toList();
		List<TraceRecord> records = window.stream().map(TraceCase::trace).toList();
		assertThat(window.stream().map(TraceCase::runId)).contains("t-002", "t-006", "t-007", "t-009", "h-005",
				"h-006");

		Set<String> product = Detector.failedNonPolicyBlock(records)
			.stream()
			.map(TraceRecord::runId)
			.collect(Collectors.toSet());
		Set<String> policy = InnerLoopFixtures.flagged(run(window));

		assertThat(policy).isEqualTo(product);
	}

	@Test
	void belowBaselineTriggerAgreesWithTheProductDetector() {
		List<TraceCase> cases = List.of(InnerLoopFixtures.reevalCase("s-below", 1.0, 0.4),
				InnerLoopFixtures.reevalCase("s-equal", 1.0, 1.0), InnerLoopFixtures.reevalCase("s-above", 0.8, 0.9));
		Set<String> product = cases.stream()
			.filter((line) -> Detector.belowBaseline(line.specContext().reevalPassRate(),
					line.specContext().baselinePassRate()))
			.map(TraceCase::runId)
			.collect(Collectors.toSet());

		Set<String> policy = InnerLoopFixtures.flagged(run(cases));

		// Equal is not below: the product's strict comparison, which the docs record as
		// the
		// reason a pack already at 1.0 never gets queued.
		assertThat(product).containsExactly("s-below");
		assertThat(policy).isEqualTo(product);
	}

	@Test
	void driftTriggerAgreesWithTheProductDetector() {
		AgentSpec pack = new AgentSpec(new AgentSpecManifest("synthetic-pack", "1.0.0"),
				GoalStepSpec.exportedGoal("synthetic-pack", "goal synthetic-pack", "BackgroundMessage"),
				List.of(ActionStepSpec.action("answer", "step answer", List.of("UserInput"), "BackgroundMessage",
						"answer the question carefully {{userInput.content}}")),
				List.of());
		String hash = SpecContentHash.of(pack);
		LifecycleMetadata recorded = new LifecycleMetadata(hash, 1.0, LifecycleState.ACTIVE, "2026-11-20T00:00:00Z");
		LifecycleMetadata stale = new LifecycleMetadata("sha256:aaaa", 1.0, LifecycleState.ACTIVE,
				"2026-11-20T00:00:00Z");
		List<TraceCase> cases = List.of(InnerLoopFixtures.driftCase("s-same", hash, hash, TraceCase.NOT_DEFECT),
				InnerLoopFixtures.driftCase("s-drifted", "sha256:aaaa", hash, TraceCase.DEFECT));

		assertThat(Detector.hasDrift(pack, recorded)).isFalse();
		assertThat(Detector.hasDrift(pack, stale)).isTrue();
		assertThat(InnerLoopFixtures.flagged(run(cases))).containsExactly("s-drifted");
	}

	@Test
	void everyDefectIsFlaggedForTheReasonTheLabelGives() {
		Map<String, PolicyRun.Verdict> verdicts = InnerLoopFixtures.byRunId(run(InnerLoopFixtures.bothSplits()));

		for (TraceCase line : InnerLoopFixtures.bothSplits()) {
			List<String> reasons = verdicts.get(line.runId()).reasons();
			if (!line.labelledDefect()) {
				assertThat(reasons).as("line " + line.runId()).isEmpty();
				continue;
			}
			// goal_not_found is the one label the product files under a different
			// trigger: a
			// failure with neither block prefix is a failed run to Detector, and the
			// corpus says
			// so in its own rule column.
			String expected = "goal_not_found".equals(line.labelReason()) ? "failed_step" : line.labelReason();
			assertThat(reasons).as("line " + line.runId() + " labelled " + line.labelReason())
				.containsExactly(expected);
		}
	}

}
