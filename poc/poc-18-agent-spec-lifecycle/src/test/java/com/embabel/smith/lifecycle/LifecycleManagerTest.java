package com.embabel.smith.lifecycle;

import com.embabel.smith.common.model.AgentSpec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline checks for the lifecycle state machine: promotion, content/score drift → degraded,
 * score collapse → retired (terminal), and the stable case.
 */
class LifecycleManagerTest {

    private final LifecycleManager lifecycle = LifecycleManager.defaults();
    private final AgentSpec healthy = LifecycleSpec.healthy();
    private final AgentSpec regressed = LifecycleSpec.regressed();
    private final String healthyHash = SpecContentHash.of(healthy);

    @Test
    void promotesFromIncubatingOnACleanBaseline() {
        assertThat(lifecycle.transition(LifecycleState.INCUBATING, false, 1.0, 1.0))
                .isEqualTo(LifecycleState.ACTIVE);
    }

    @Test
    void contentDriftAloneDegradesAnActiveSpec() {
        // Prompt edited (hash differs) but the score is unchanged.
        assertThat(lifecycle.transition(LifecycleState.ACTIVE, true, 1.0, 1.0))
                .isEqualTo(LifecycleState.DEGRADED);
    }

    @Test
    void scoreDriftBeyondToleranceDegradesAnActiveSpec() {
        // Score dropped 0.2 (tolerance 0.1) but still above the retire floor.
        assertThat(lifecycle.transition(LifecycleState.ACTIVE, false, 1.0, 0.8))
                .isEqualTo(LifecycleState.DEGRADED);
    }

    @Test
    void aSmallScoreWobbleWithinToleranceStaysActive() {
        assertThat(lifecycle.transition(LifecycleState.ACTIVE, false, 1.0, 0.95))
                .isEqualTo(LifecycleState.ACTIVE);
    }

    @Test
    void aScoreCollapseRetiresTheSpec() {
        assertThat(lifecycle.transition(LifecycleState.ACTIVE, false, 1.0, 0.0))
                .isEqualTo(LifecycleState.RETIRED);
    }

    @Test
    void retiredIsTerminal() {
        assertThat(lifecycle.transition(LifecycleState.RETIRED, false, 1.0, 1.0))
                .isEqualTo(LifecycleState.RETIRED);
    }

    @Test
    void aDriftingIncubatingSpecStaysIncubating() {
        assertThat(lifecycle.transition(LifecycleState.INCUBATING, true, 0.5, 0.5))
                .isEqualTo(LifecycleState.INCUBATING);
    }

    @Test
    void assessReportsContentDriftAndTheTransition() {
        DriftReport report = lifecycle.assess(LifecycleSpec.SPEC_ID, LifecycleState.ACTIVE,
                healthyHash, regressed, 1.0, 0.0);
        assertThat(report.contentDrift()).isTrue();
        assertThat(report.after()).isEqualTo(LifecycleState.RETIRED);
        assertThat(report.drifted()).isTrue();
        assertThat(report.recordedHash()).isEqualTo(healthyHash);
        assertThat(report.currentHash()).isEqualTo(SpecContentHash.of(regressed));
    }

    @Test
    void assessReportsStableForAnUnchangedSpec() {
        DriftReport report = lifecycle.assess(LifecycleSpec.SPEC_ID, LifecycleState.ACTIVE,
                healthyHash, healthy, 1.0, 1.0);
        assertThat(report.contentDrift()).isFalse();
        assertThat(report.after()).isEqualTo(LifecycleState.ACTIVE);
        assertThat(report.drifted()).isFalse();
    }
}
