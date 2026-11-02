package com.embabel.smith.lifecycle;

import com.embabel.smith.common.model.SkillPack;

/**
 * The SkillOps-style proactive re-eval (doc 02 §5 caveat 4, doc 03 §5): re-score a skill, compare
 * its content hash and eval pass-rate against the recorded baseline, and move it through the
 * lifecycle. Pure and deterministic — the scores are inputs, so the whole state machine is
 * verifiable offline without a model.
 */
public final class LifecycleManager {

    private final DriftDetector drift = new DriftDetector();
    private final double activeThreshold;   // baseline score required to be promoted to ACTIVE
    private final double driftTolerance;     // score-drop tolerance before flagging score drift
    private final double retireFloor;        // score below which the contract is violated

    public LifecycleManager(double activeThreshold, double driftTolerance, double retireFloor) {
        this.activeThreshold = activeThreshold;
        this.driftTolerance = driftTolerance;
        this.retireFloor = retireFloor;
    }

    /** Sane defaults: promote at &ge;0.8, flag a drop of &gt;0.1, retire below 0.2. */
    public static LifecycleManager defaults() {
        return new LifecycleManager(0.8, 0.1, 0.2);
    }

    /**
     * Re-assess a skill. {@code state} is its recorded lifecycle state, {@code recordedHash} the
     * content hash at baseline, {@code current} the skill as it is now, and
     * {@code baselineScore} / {@code currentScore} its recorded and freshly-measured eval
     * pass-rates.
     */
    public DriftReport assess(String skill, LifecycleState state, String recordedHash,
                              SkillPack current, double baselineScore, double currentScore) {
        boolean contentDrift = drift.contentDrift(recordedHash, current);
        LifecycleState after = transition(state, contentDrift, baselineScore, currentScore);
        return new DriftReport(skill, state, after, contentDrift, baselineScore, currentScore,
                recordedHash, SkillContentHash.of(current));
    }

    /** The pure lifecycle transition, given the drift signals and the two scores. */
    public LifecycleState transition(LifecycleState state, boolean contentDrift,
                                     double baselineScore, double currentScore) {
        if (state == LifecycleState.RETIRED) {
            return LifecycleState.RETIRED; // terminal
        }
        if (currentScore < retireFloor) {
            return LifecycleState.RETIRED; // contract violated
        }
        boolean scoreDrift = drift.scoreDrift(baselineScore, currentScore, driftTolerance);
        if (contentDrift || scoreDrift) {
            // A not-yet-active skill just hasn't validated; flag it but keep it incubating.
            return state == LifecycleState.INCUBATING ? LifecycleState.INCUBATING : LifecycleState.DEGRADED;
        }
        if (state == LifecycleState.INCUBATING && baselineScore >= activeThreshold) {
            return LifecycleState.ACTIVE; // promoted on a clean baseline
        }
        return state;
    }
}
