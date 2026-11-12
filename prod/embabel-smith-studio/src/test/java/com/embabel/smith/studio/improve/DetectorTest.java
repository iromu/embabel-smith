package com.embabel.smith.studio.improve;

import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.builder.lifecycle.LifecycleState;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.runtime.trace.TraceRecord;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DetectorTest {

    private final Detector detector = new Detector();

    @Test
    void failedRunQueues() {
        AgentSpec pack = TestFixtures.pack("extract", "s1", "s2");
        LifecycleMetadata sidecar = TestFixtures.sidecar(pack, 1.0, LifecycleState.ACTIVE);
        List<TraceRecord> window = List.of(TestFixtures.failedTrace("r1", "extract", "input",
                TestFixtures.step("s1", "completed", "ok"),
                TestFixtures.step("s2", "failed", "bad")));

        Detector.Result result = detector.detect("extract", pack, sidecar, window);

        assertThat(result.queued()).isTrue();
        assertThat(result.reasons()).contains("failed runs");
        assertThat(result.failureTraces()).hasSize(1);
    }

    @Test
    void tierBlockDoesNotQueue() {
        AgentSpec pack = TestFixtures.pack("extract", "s1", "s2");
        LifecycleMetadata sidecar = TestFixtures.sidecar(pack, 1.0, LifecycleState.ACTIVE);
        List<TraceRecord> window = List.of(TestFixtures.tierBlockTrace("r1", "extract", "input"));

        Detector.Result result = detector.detect("extract", pack, sidecar, window);

        assertThat(result.queued()).isFalse();
        assertThat(result.reasons()).isEmpty();
        assertThat(result.failureTraces()).isEmpty();
    }

    @Test
    void contentDriftQueues() {
        AgentSpec pack = TestFixtures.pack("extract", "s1", "s2");
        // A sidecar that records a different content hash than the current pack (drift).
        LifecycleMetadata drifted = new LifecycleMetadata("some-other-hash", 1.0,
                LifecycleState.ACTIVE, "t");

        Detector.Result result = detector.detect("extract", pack, drifted, List.of());

        assertThat(result.queued()).isTrue();
        assertThat(result.reasons()).contains("content drift");
    }

    @Test
    void cleanWindowDoesNotQueue() {
        AgentSpec pack = TestFixtures.pack("extract", "s1", "s2");
        LifecycleMetadata sidecar = TestFixtures.sidecar(pack, 1.0, LifecycleState.ACTIVE);
        List<TraceRecord> window = List.of(TestFixtures.okTrace("r1", "extract", "input",
                TestFixtures.step("s1", "completed", "ok"),
                TestFixtures.step("s2", "completed", "ok")));

        Detector.Result result = detector.detect("extract", pack, sidecar, window);

        assertThat(result.queued()).isFalse();
        assertThat(result.reasons()).isEmpty();
    }

    @Test
    void reevalBelowBaselineQueues() {
        AgentSpec pack = TestFixtures.pack("extract", "s1");
        LifecycleMetadata sidecar = TestFixtures.sidecar(pack, 0.9, LifecycleState.ACTIVE);

        Detector.Result result = detector.detect("extract", pack, sidecar, List.of(), spec -> 0.5);

        assertThat(result.queued()).isTrue();
        assertThat(result.reasons()).contains("below baseline");
    }
}
