package com.embabel.smith.lifecycle;

import com.embabel.smith.common.model.SkillPack;

/**
 * The two drift signals for a skill: <em>content drift</em> (the pack's content hash diverged from
 * the recorded baseline) and <em>score drift</em> (its eval pass-rate dropped below the baseline by
 * more than a tolerance). Both are the "skill silently breaks" failure mode (doc 02 §5 caveat 4).
 */
public final class DriftDetector {

    /** True when the skill's content no longer matches the hash recorded at baseline. */
    public boolean contentDrift(String recordedHash, SkillPack current) {
        return !recordedHash.equals(SkillContentHash.of(current));
    }

    /** True when the current score has fallen below the baseline by more than {@code tolerance}. */
    public boolean scoreDrift(double baselineScore, double currentScore, double tolerance) {
        return currentScore < baselineScore - tolerance;
    }
}
