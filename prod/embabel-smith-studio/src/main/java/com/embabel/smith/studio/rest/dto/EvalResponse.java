package com.embabel.smith.studio.rest.dto;

import java.util.List;

/**
 * The outcome of evaluating a skill (the Eval pane): the final pass-rate score, how many prompt
 * rewrites were applied, the tasks still failing, whether the gate was met, and the revised
 * terminal prompt when a rewrite was applied. A missing endpoint is a structured
 * {@code MODEL_NOT_CONFIGURED} error.
 */
public record EvalResponse(String error, String errorCode, String skill, Double score,
                           int refinementCount, List<String> failingTasks, boolean passed,
                           String revisedPrompt) {

    public static EvalResponse ok(String skill, double score, int refinementCount,
                                  List<String> failingTasks, boolean passed, String revisedPrompt) {
        return new EvalResponse(null, null, skill, score, refinementCount, failingTasks, passed,
                revisedPrompt);
    }

    public static EvalResponse error(String error) {
        return new EvalResponse(error, null, null, null, 0, List.of(), false, null);
    }

    /** The structured "no model endpoint" error. */
    public static EvalResponse noEndpoint(String message) {
        return new EvalResponse(message, "MODEL_NOT_CONFIGURED", null, null, 0, List.of(), false, null);
    }
}
