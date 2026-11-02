package com.embabel.smith.lifecycle;

import com.embabel.smith.common.model.SkillPack;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline checks for the two drift signals: content drift (hash mismatch) and score drift
 * (pass-rate drop beyond a tolerance).
 */
class DriftDetectorTest {

    private final DriftDetector drift = new DriftDetector();
    private final SkillPack pack = LifecycleSkill.healthy();
    private final String hash = SkillContentHash.of(pack);

    @Test
    void noContentDriftWhenThePackIsUnchanged() {
        assertThat(drift.contentDrift(hash, pack)).isFalse();
    }

    @Test
    void contentDriftWhenThePackChanges() {
        assertThat(drift.contentDrift(hash, LifecycleSkill.reworded())).isTrue();
        assertThat(drift.contentDrift(hash, LifecycleSkill.regressed())).isTrue();
    }

    @Test
    void noScoreDriftWithinTolerance() {
        assertThat(drift.scoreDrift(1.0, 0.95, 0.1)).isFalse();
        assertThat(drift.scoreDrift(1.0, 0.9, 0.1)).isFalse();
    }

    @Test
    void scoreDriftBeyondTolerance() {
        assertThat(drift.scoreDrift(1.0, 0.8, 0.1)).isTrue();
        assertThat(drift.scoreDrift(1.0, 0.0, 0.1)).isTrue();
    }
}
