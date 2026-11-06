package com.embabel.smith.builder;

/**
 * The per-build knobs (design D1): the refinement iteration bound, the eval score gate, and the
 * publish flags. Defaults: up to 3 refinement rounds, a score gate of 1.0 (every eval task must
 * pass), no overwrite, no push.
 */
public record BuildOptions(int maxRefine, double scoreGate, boolean overwrite, boolean push) {

    public static final int DEFAULT_MAX_REFINE = 3;
    public static final double DEFAULT_SCORE_GATE = 1.0;

    public static BuildOptions defaults() {
        return new BuildOptions(DEFAULT_MAX_REFINE, DEFAULT_SCORE_GATE, false, false);
    }
}
