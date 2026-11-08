package com.embabel.smith.studio.rest.dto;

import com.embabel.smith.runtime.trace.TraceRecord;

import java.util.List;

/**
 * One run's trace record (the Traces pane): run time, source ref, success/failure, the per-step
 * records (for step-level drill-down), and the failure reason where present.
 */
public record TraceInfo(String runId, String timestamp, String sourceUri, String sourceRef,
                        String resolvedAt, String pack, String goal, String input, String planner,
                        String trustTier, List<StepInfo> steps, boolean success, String error,
                        String outputExcerpt) {

    public static TraceInfo of(TraceRecord record) {
        TraceRecord.SourceRef source = record.source();
        TraceRecord.PackRef pack = record.pack();
        List<StepInfo> steps = record.steps().stream().map(StepInfo::of).toList();
        return new TraceInfo(
                record.runId(),
                record.timestamp().toString(),
                source == null ? null : source.uri(),
                source == null ? null : source.ref(),
                source == null ? null : source.resolvedAt(),
                pack == null ? null : pack.name(),
                record.goal(),
                record.input(),
                record.planner(),
                record.trustTier(),
                steps,
                record.success(),
                record.error(),
                record.outputExcerpt());
    }
}
