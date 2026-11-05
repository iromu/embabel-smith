package com.embabel.smith.runtime.trace;

import java.time.Instant;
import java.util.List;

/**
 * One trace record per run, appended to the JSONL trace store (schema per design.md).
 *
 * <p>{@code source.ref} is the pinned ref of a git source ({@code null} for folder sources or
 * unpinned git sources); {@code source.resolvedAt} is the commit SHA the git source materialized
 * at ({@code null} for folder sources). {@code pack} names the pack that exports the run's goal
 * ({@code null} when the goal was not found). {@code steps} is empty when the run never executed
 * (tier block, goal not found, or no plan reachable).
 */
public record TraceRecord(
        String runId,
        Instant timestamp,
        SourceRef source,
        PackRef pack,
        String goal,
        String input,
        String planner,
        String trustTier,
        List<StepRecord> steps,
        boolean success,
        String error,
        String outputExcerpt) {

    public record SourceRef(String uri, String ref, String resolvedAt) {
    }

    public record PackRef(String name, String contentHash) {
    }

    /** One executed step: name, status ({@code completed}/{@code failed}), duration, output excerpt. */
    public record StepRecord(String name, String status, long durationMs, String outputExcerpt) {
    }
}
