package com.embabel.smith.unified.dto;

import java.util.List;

/**
 * The result of the eval/refinement pane (POC 9): the composite score before (and, when a prompt
 * rewrite ran, after) refinement, the step refined, the revised prompt, and the inputs that failed.
 * {@link #error} is non-null when the request could not run (no spec selected, an unknown spec, or
 * no endpoint configured) — in which case the score/refinement fields are empty.
 */
public record EvalResponse(
        String error,
        String specName,
        String stepName,
        double beforeScore,
        double afterScore,
        boolean refined,
        boolean improved,
        String revisedPrompt,
        List<String> failingInputs) {

    public static EvalResponse error(String error) {
        return new EvalResponse(error, null, null, 0.0, 0.0, false, false, null, List.of());
    }

    public static EvalResponse ok(String specName, String stepName, double before, double after,
                                 boolean refined, boolean improved, String revisedPrompt,
                                 List<String> failingInputs) {
        return new EvalResponse(null, specName, stepName, round4(before), round4(after), refined,
                improved, revisedPrompt, failingInputs);
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
