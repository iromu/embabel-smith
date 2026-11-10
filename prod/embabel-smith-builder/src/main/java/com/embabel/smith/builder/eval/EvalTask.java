package com.embabel.smith.builder.eval;

import java.util.List;

/**
 * One acceptance case for the built skill: an {@code input} to run the skill on and the
 * {@code expected} substring that a passing run's output must contain. The task set is a
 * parameter of the build (design D1) — the builder does not hardcode it.
 */
public record EvalTask(String name, String input, String expected) {

    /**
     * The default smoke task set: run the skill on a generic prompt and require that it produces
     * non-blank output (a blank {@code expected} means "produced output", see {@link EvalScorer}).
     * Centralized here so the CLI, the studio's builder/eval/loop wiring, and the eval fallback
     * all share one definition instead of drifting apart.
     */
    public static List<EvalTask> smokeDefault() {
        return List.of(new EvalTask("runs-and-produces-output", "Describe your use case.", ""));
    }
}
