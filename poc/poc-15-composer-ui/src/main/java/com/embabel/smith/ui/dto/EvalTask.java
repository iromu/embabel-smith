package com.embabel.smith.ui.dto;

import java.util.List;

/**
 * One eval task as submitted from the canvas: a user input, a human-readable acceptance criterion
 * (fed to the prompt rewriter when the task fails), and the substring the goal output must contain
 * for the task to pass — a simple, JSON-serializable stand-in for the deterministic acceptance check.
 */
public record EvalTask(String input, String acceptanceCriteria, String mustContain) {

    /** The default task set when the request supplies none — a couple of goal-type probes. */
    public static final List<EvalTask> DEFAULTS = List.of(
            new EvalTask("Explain how a coffee machine works",
                    "a plain-language explainer about coffee machines", "coffee"),
            new EvalTask("Explain what a black hole is",
                    "an explanation of black holes", "black hole"));
}
