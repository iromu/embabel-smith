package com.embabel.smith.studio.improve;

import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.builder.security.ScanReport;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.runtime.trace.TraceRecord;

import java.util.List;

/**
 * A gated candidate held in the approval queue, awaiting an explicit decision (change 06's UI).
 * Carries the evidence that produced it: the triggering failure traces, the re-eval score, the
 * security scan, the proposed lifecycle (new hash, new baseline, new state), and a summary of what
 * the candidate changes relative to the current pack.
 */
public record PendingCandidate(
        String id,
        String spec,
        AgentSpec candidate,
        LifecycleMetadata proposedLifecycle,
        double score,
        double baseline,
        ScanReport scan,
        List<TraceRecord> evidence,
        String diffSummary) {
}
