package com.embabel.smith.builder.lifecycle;

/**
 * The lifecycle of a spec in the library (TrackRL-style: incubation → retention → retirement).
 * A spec's <em>contract</em> is its eval pass-rate; drift is the divergence of its content or its
 * score from the recorded baseline (doc 02 §5 caveat 4, doc 03 §1 "Spec decay / lifecycle").
 * Extracted from POC 18.
 */
public enum LifecycleState {

    /** New; not yet validated by a passing baseline eval. */
    INCUBATING,

    /** Baseline eval passed and no drift — the spec is trusted. */
    ACTIVE,

    /** Drift detected (content or score) but the spec still functions — needs re-eval. */
    DEGRADED,

    /** The spec's contract is violated (its score collapsed) — terminal, out of the library. */
    RETIRED
}
