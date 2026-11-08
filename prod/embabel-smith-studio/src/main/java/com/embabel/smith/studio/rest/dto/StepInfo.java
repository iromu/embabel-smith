package com.embabel.smith.studio.rest.dto;

/** One executed step of a run (mirrors {@code TraceRecord.StepRecord}). */
public record StepInfo(String name, String status, long durationMs, String outputExcerpt) {

    public static StepInfo of(com.embabel.smith.runtime.trace.TraceRecord.StepRecord step) {
        return new StepInfo(step.name(), step.status(), step.durationMs(), step.outputExcerpt());
    }
}
