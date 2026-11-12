package com.embabel.smith.ui.dto;

import com.embabel.smith.refinement.RefinementRunner;

import java.util.List;

/**
 * The result of the eval/refinement pane: the composite score before (and, when a prompt rewrite ran,
 * after) refinement, the step that was refined, the revised prompt, and the inputs that failed.
 * {@link #error} is non-null when the request could not be run (no spec selected, unknown spec, or
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

    public static EvalResponse from(RefinementRunner.RefinementOutcome outcome,
                                    String specName, String stepName) {
        return new EvalResponse(
                null,
                specName,
                stepName,
                round4(outcome.beforeScore()),
                round4(outcome.afterScore()),
                outcome.revisedPrompt() != null,
                outcome.improved(),
                outcome.revisedPrompt(),
                outcome.failingInputs());
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
