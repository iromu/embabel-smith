package com.embabel.smith.studio.improve;

import com.embabel.smith.builder.security.ScanReport;

import java.util.List;

/**
 * The outcome of running the gate chain on a candidate pack: whether it cleared every gate (and may
 * be published), or the stage and reasons it was rejected at. Carries the re-eval score and the
 * security scan so a rejection is recorded with evidence.
 */
public record GateResult(boolean gated, String stage, List<String> reasons, Double score, ScanReport scan) {

    /** A candidate that cleared every gate (re-eval beat the baseline). */
    public static GateResult pass(double score, ScanReport scan) {
        return new GateResult(true, null, List.of(), score, scan);
    }

    /** A candidate rejected at a named stage, with the recorded reasons. */
    public static GateResult reject(String stage, List<String> reasons, Double score, ScanReport scan) {
        return new GateResult(false, stage, List.copyOf(reasons), score, scan);
    }
}
