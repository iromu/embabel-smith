/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest.dto;

import java.util.List;

import com.embabel.smith.builder.improve.PendingCandidate;

/**
 * A pending candidate awaiting approval (the Auto-improve pane), with its evidence: the
 * re-eval score vs the baseline, the security scan, the triggering failure traces, a diff
 * summary, and the lifecycle state it would transition to.
 *
 * @param id the candidate id.
 * @param spec the spec the candidate belongs to.
 * @param score the re-eval score.
 * @param baseline the baseline score.
 * @param diffSummary the diff summary of the candidate change.
 * @param scan the security scan of the candidate.
 * @param evidence the triggering failure traces.
 * @param proposedState the lifecycle state the candidate would transition to.
 * @author Ivan Rodriguez
 */
public record CandidateInfo(String id, String spec, double score, double baseline, String diffSummary, ScanInfo scan,
		List<TraceInfo> evidence, String proposedState) {

	public static CandidateInfo of(PendingCandidate candidate) {
		List<TraceInfo> evidence = candidate.evidence().stream().map(TraceInfo::of).toList();
		return new CandidateInfo(candidate.id(), candidate.spec(), candidate.score(), candidate.baseline(),
				candidate.diffSummary(), (candidate.scan() != null) ? ScanInfo.of(candidate.scan()) : null, evidence,
				candidate.proposedLifecycle().state().name());
	}
}
