package com.embabel.smith.studio.rest.dto;

/**
 * The outcome of a run: success, the goal output (or the failure reason), the run id (so the run is
 * findable in the Traces pane), and a structured error code when the run could not proceed.
 */
public record RunResponse(boolean success, String output, String error, String errorCode, String runId) {

    public static RunResponse ok(String output, String runId) {
        return new RunResponse(true, output, null, null, runId);
    }

    public static RunResponse failed(String error, String runId) {
        return new RunResponse(false, null, error, null, runId);
    }

    public static RunResponse error(String error) {
        return new RunResponse(false, null, error, null, null);
    }

    /** The structured "no model endpoint" error (the endpoint-gated panes render a banner for it). */
    public static RunResponse noEndpoint(String message) {
        return new RunResponse(false, null, message, "MODEL_NOT_CONFIGURED", null);
    }
}
