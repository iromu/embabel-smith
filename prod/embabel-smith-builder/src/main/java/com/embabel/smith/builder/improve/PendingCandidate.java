/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.improve;

import java.util.List;

import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.builder.security.ScanReport;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.trace.TraceRecord;

/**
 * A gated candidate held in the approval queue, awaiting an explicit decision (change
 * 06's UI). Carries the evidence that produced it: the triggering failure traces, the
 * re-eval score, the security scan, the proposed lifecycle (new hash, new baseline, new
 * state), and a summary of what the candidate changes relative to the current pack.
 *
 * @param id the candidate's unique id
 * @param spec the spec the candidate refines
 * @param candidate the refined candidate pack
 * @param proposedLifecycle the proposed lifecycle sidecar (new hash, baseline, state)
 * @param score the candidate's re-eval pass-rate
 * @param baseline the pack's baseline pass-rate
 * @param scan the security scan report
 * @param evidence the triggering failure traces
 * @param diffSummary a summary of what the candidate changes
 * @author Ivan Rodriguez
 */
public record PendingCandidate(String id, String spec, AgentSpec candidate, LifecycleMetadata proposedLifecycle,
		double score, double baseline, ScanReport scan, List<TraceRecord> evidence, String diffSummary) {
}
