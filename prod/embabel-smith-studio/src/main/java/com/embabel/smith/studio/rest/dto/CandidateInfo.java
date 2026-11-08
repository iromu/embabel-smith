package com.embabel.smith.studio.rest.dto;

import com.embabel.smith.studio.improve.PendingCandidate;

import java.util.List;

/**
 * A pending candidate awaiting approval (the Auto-improve pane), with its evidence: the re-eval
 * score vs the baseline, the security scan, the triggering failure traces, a diff summary, and the
 * lifecycle state it would transition to.
 */
public record CandidateInfo(String id, String skill, double score, double baseline,
                            String diffSummary, ScanInfo scan, List<TraceInfo> evidence,
                            String proposedState) {

    public static CandidateInfo of(PendingCandidate candidate) {
        List<TraceInfo> evidence = candidate.evidence().stream().map(TraceInfo::of).toList();
        return new CandidateInfo(
                candidate.id(),
                candidate.skill(),
                candidate.score(),
                candidate.baseline(),
                candidate.diffSummary(),
                candidate.scan() == null ? null : ScanInfo.of(candidate.scan()),
                evidence,
                candidate.proposedLifecycle().state().name());
    }
}
