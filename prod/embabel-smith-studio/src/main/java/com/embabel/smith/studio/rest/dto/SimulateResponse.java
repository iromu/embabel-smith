package com.embabel.smith.studio.rest.dto;

import com.embabel.smith.runtime.AgentSpecRunService;
import com.embabel.smith.runtime.trace.TraceRecord;

import java.util.List;

/**
 * The simulate response: the run outcome plus the per-step trace, so the UI can show the generated
 * flow executing step by step. {@code errorCode} is {@code MODEL_NOT_CONFIGURED} when the endpoint
 * is absent.
 */
public record SimulateResponse(
        boolean success,
        String output,
        String error,
        String errorCode,
        List<Step> steps) {

    /** One executed step: name, status, duration, and a short output excerpt. */
    public record Step(String name, String status, long durationMs, String outputExcerpt) {
    }

    public static SimulateResponse of(AgentSpecRunService.RunResult result) {
        TraceRecord trace = result.trace();
        List<Step> steps = trace == null || trace.steps() == null
                ? List.of()
                : trace.steps().stream()
                        .map(s -> new Step(s.name(), s.status(), s.durationMs(), s.outputExcerpt()))
                        .toList();
        return new SimulateResponse(result.success(), result.output(), result.error(), null, steps);
    }

    public static SimulateResponse noEndpoint(String message) {
        return new SimulateResponse(false, null, message, "MODEL_NOT_CONFIGURED", List.of());
    }

    public static SimulateResponse failed(String message) {
        return new SimulateResponse(false, null, message, null, List.of());
    }
}
