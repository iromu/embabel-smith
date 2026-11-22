/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.control;

import java.util.List;

import com.embabel.smith.builder.improve.ApprovalQueue;
import com.embabel.smith.builder.improve.ApprovalQueue.LoopPublisher;
import com.embabel.smith.builder.improve.LifecycleTransitions;
import com.embabel.smith.builder.improve.PendingCandidate;
import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.builder.lifecycle.LifecycleState;
import com.embabel.smith.builder.security.AgentSpecSecurityScanner;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.selfimprove.referee.Candidate;
import com.embabel.smith.selfimprove.referee.RefereeReport;

/**
 * Human control for the loop: a candidate that cleared the gate is <em>held</em> in the
 * product's own approval queue and nothing is published. The fixed artifact is the
 * {@code human-control} row of {@code evidence/MUTABILITY.md}, which fixes approval-queue
 * holds, auto-publish off and commit-never-push as things a generation may not turn;
 * {@code docs/10-self-improvement.md} states the same rule for the product loop
 * ("Candidates hold for approval by default"). This class is that rule for POC 27, reused
 * rather than restated: the queue is {@code builder.improve.ApprovalQueue} and the record
 * it holds is {@code PendingCandidate}, so the held count is the same number the studio's
 * own approve/reject surface reads, not a tally invented next to it.
 *
 * <p>
 * <b>Publishing is not offered.</b> The class has no method that returns a publish
 * result, and the {@link LoopPublisher} it hands {@code ApprovalQueue.enqueue} is one
 * that throws: the queue's auto-publish branch and its approve path both go through that
 * argument, so a caller cannot reach a write through this object even by asking for
 * auto-publish. An unattended loop that publishes is the failure the design's Non-Goals
 * name, and the POC's paid outer loop is exactly such a caller.
 *
 * <p>
 * <b>What this does not stop.</b> A human approving a held candidate in the studio UI
 * publishes it, and that is the intended end of the story — the queue holds, the person
 * decides. Nothing here can stop that, and nothing here tries. What is enforced is that
 * <em>this</em> object never does it: see the graph walk and the source scan in
 * {@code LoopNeverPushesTest} for what that claim is checked against, including its
 * limits.
 *
 * @author Ivan Rodriguez
 */
public final class HeldForApproval {

	/**
	 * The publisher this POC hands a queue. It refuses rather than writing, and names the
	 * fixed artifact it is honouring. It is needed at all because
	 * {@code ApprovalQueue.enqueue} takes a publisher even on the hold path, where it is
	 * never called; handing it one that could write would make "publish nothing" depend
	 * on the flag staying false.
	 */
	private static final LoopPublisher NOTHING_PUBLISHED = (candidate) -> {
		throw new IllegalStateException("candidate '" + candidate.id() + "' was not published: POC 27 holds gated"
				+ " candidates for a human decision and publishes none. evidence/MUTABILITY.md fixes human-control"
				+ " (approval queue holds, auto-publish off, commit-never-push) as an artifact a generation may not"
				+ " turn, so the loop has no code path that writes a pack to a source. The candidate stays held.");
	};

	private final ApprovalQueue queue = new ApprovalQueue();

	private final AgentSpecSecurityScanner scanner = new AgentSpecSecurityScanner();

	/**
	 * Holds one candidate that cleared the gate.
	 * @param candidate the variant the referee scored
	 * @param report the referee's verdict on it; it must record a pass
	 * @param baseline the pass-rate the candidate had to beat, recorded on the held
	 * record so the human sees what it beat rather than having to trust the loop
	 * @throws IllegalStateException if the report did not gate the candidate, or gated it
	 * without a score: an ungated candidate in the queue is one a human would approve
	 * without the gate having passed, and a pass with no score is a report built wrong
	 */
	public void hold(Candidate candidate, RefereeReport report, double baseline) {
		if (!report.gated()) {
			throw new IllegalStateException("candidate '" + candidate.name() + "' is not held: the referee did not"
					+ " gate it (rejected at " + report.gateStage() + ", reasons " + report.gateReasons() + "). Only a"
					+ " candidate that cleared every gate may enter the approval queue.");
		}
		Double gateScore = report.gateScore();
		if (gateScore == null) {
			throw new IllegalStateException("candidate '" + candidate.name() + "' is reported gated but carries no"
					+ " re-eval score, so the held record could not state what it achieved.");
		}
		AgentSpec pack = candidate.spec();
		LifecycleMetadata proposed = LifecycleTransitions.sidecarFor(pack, gateScore,
				LifecycleTransitions.onPublished(LifecycleState.INCUBATING));
		this.queue.enqueue(new PendingCandidate(candidate.name(), pack.name(), pack, proposed, gateScore, baseline,
				this.scanner.scan(pack), List.of(), diff(report)), false, NOTHING_PUBLISHED);
	}

	/**
	 * How many candidates are held awaiting a decision. This is the number the POC
	 * reports as its approval-queue yield; it is a reported number, not a target.
	 * @return the held count.
	 */
	public int held() {
		return this.queue.size();
	}

	/**
	 * The held candidates, in the order they were held.
	 * @return their identifiers, so a run record can name what is waiting rather than
	 * only how much.
	 */
	public List<String> heldIds() {
		return this.queue.pending().stream().map(PendingCandidate::id).toList();
	}

	private static String diff(RefereeReport report) {
		return "gate re-eval " + String.format("%.3f", report.gateScore()) + ", frozen check "
				+ String.format("%.3f", report.frozenScore()) + " over " + report.scoredTaskIds().size()
				+ " frozen tasks; excluded " + report.excludedProposedTaskIds().size() + " proposed task(s)";
	}

}
