package com.embabel.smith.unified.dto;

/**
 * The result of a run (POC 1): the goal output, or a clear error. {@link #error} is non-null when
 * the run could not happen (no skill selected, an unknown skill, no endpoint configured, or the plan
 * failed) — in which case {@link #output} is null.
 */
public record RunResponse(String error, String output) {

    public static RunResponse ok(String output) {
        return new RunResponse(null, output);
    }

    public static RunResponse error(String error) {
        return new RunResponse(error, null);
    }

    public static RunResponse noEndpoint(String error) {
        return new RunResponse(error, null);
    }
}
