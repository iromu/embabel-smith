package com.embabel.smith.studio.loop;

import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.builder.lifecycle.LifecycleState;
import com.embabel.smith.builder.publish.PublishResult;
import com.embabel.smith.builder.publish.Publisher;
import com.embabel.smith.common.config.ModelEndpointConfig;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.common.model.SkillPackManifest;
import com.embabel.smith.studio.improve.ApprovalQueue;
import com.embabel.smith.studio.improve.ImprovementLoop;
import com.embabel.smith.studio.improve.PendingCandidate;
import com.embabel.smith.studio.rest.dto.ApproveResponse;
import com.embabel.smith.studio.rest.dto.IterationInfo;
import com.embabel.smith.studio.rest.dto.RejectResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Offline tests for the loop control surface (the only production auto-publish path): a missing
 * model endpoint is reported without running; auto-publish approves and publishes each produced
 * candidate; a publish failure leaves the candidate pending; hold mode queues without publishing;
 * and approve/reject on an unknown id surface the error.
 */
@ExtendWith(MockitoExtension.class)
class LoopServiceTest {

    @Mock
    ImprovementLoop loop;

    @Mock
    Publisher publisher;

    private static ModelEndpointConfig configured() {
        return new ModelEndpointConfig("http://localhost:4000", "key", "m1", "m1");
    }

    private static ModelEndpointConfig missing() {
        return new ModelEndpointConfig("", "", "", "");
    }

    private static PendingCandidate candidate(String id, String skill) {
        SkillPack pack = new SkillPack(
                new SkillPackManifest(skill, "1.0.0"),
                GoalStepSpec.exportedGoal(skill, "goal " + skill, "BackgroundMessage"),
                List.of(ActionStepSpec.action("s1", "step s1", List.of("UserInput"),
                        "BackgroundMessage", "do it")),
                List.of());
        LifecycleMetadata lifecycle = new LifecycleMetadata("hash", 1.0, LifecycleState.ACTIVE,
                "2026-01-01T00:00:00Z");
        return new PendingCandidate(id, skill, pack, lifecycle, 1.0, 0.5, null, List.of(), "rewrote s1");
    }

    @Test
    void triggerWithoutEndpointReportsModelNotConfigured() {
        LoopService service = new LoopService(loop, new LoopSchedulerState(true), publisher, missing());

        IterationInfo info = service.trigger();

        assertThat(info.ran()).isFalse();
        assertThat(info.errorCode()).isEqualTo("MODEL_NOT_CONFIGURED");
        verify(loop, never()).runOnce();
    }

    @Test
    void autoPublishApprovesAndPublishesProducedCandidates() throws IOException {
        PendingCandidate candidate = candidate("c1", "extract");
        ApprovalQueue queue = new ApprovalQueue();
        queue.enqueue(candidate, false, x -> new PublishResult(Path.of("/tmp/extract"), false)); // held
        when(loop.runOnce()).thenReturn(List.of(candidate));
        when(loop.queue()).thenReturn(queue);
        when(publisher.publish(any(), any(), anyBoolean(), anyBoolean()))
                .thenReturn(new PublishResult(Path.of("/tmp/extract"), true));
        LoopService service = new LoopService(loop, new LoopSchedulerState(true), publisher, configured());

        IterationInfo info = service.trigger();

        assertThat(info.ran()).isTrue();
        assertThat(info.candidates()).isEqualTo(1);
        assertThat(info.publishedSkills()).containsExactly("extract");
        assertThat(info.queuedSkills()).isEmpty();
        // The loop pushes improvements into the source (a git source's commit must survive the
        // publisher's throwaway clone); the folder publisher ignores the flag.
        verify(publisher).publish(any(), any(), anyBoolean(), eq(true));
    }

    @Test
    void aPublishFailureLeavesTheCandidatePending() throws IOException {
        PendingCandidate candidate = candidate("c1", "extract");
        ApprovalQueue queue = new ApprovalQueue();
        queue.enqueue(candidate, false, x -> new PublishResult(Path.of("/tmp/extract"), false));
        when(loop.runOnce()).thenReturn(List.of(candidate));
        when(loop.queue()).thenReturn(queue);
        when(publisher.publish(any(), any(), anyBoolean(), anyBoolean()))
                .thenThrow(new IOException("boom"));
        LoopService service = new LoopService(loop, new LoopSchedulerState(true), publisher, configured());

        IterationInfo info = service.trigger();

        assertThat(info.publishedSkills()).isEmpty();
        assertThat(info.queuedSkills()).containsExactly("extract"); // left pending for a manual decision
        assertThat(queue.size()).isEqualTo(1);
    }

    @Test
    void holdModeQueuesWithoutPublishing() throws IOException {
        PendingCandidate candidate = candidate("c1", "extract");
        when(loop.runOnce()).thenReturn(List.of(candidate));
        LoopService service = new LoopService(loop, new LoopSchedulerState(false), publisher, configured());

        IterationInfo info = service.trigger();

        assertThat(info.publishedSkills()).isEmpty();
        assertThat(info.queuedSkills()).containsExactly("extract");
        verify(publisher, never()).publish(any(), any(), anyBoolean(), anyBoolean());
    }

    @Test
    void approvingAnUnknownIdReportsTheError() {
        when(loop.queue()).thenReturn(new ApprovalQueue());
        LoopService service = new LoopService(loop, new LoopSchedulerState(false), publisher, configured());

        ApproveResponse response = service.approve("missing");

        assertThat(response.ok()).isFalse();
        assertThat(response.error()).isEqualTo("No pending candidate with id missing");
    }

    @Test
    void rejectingAnUnknownIdReportsTheError() {
        when(loop.queue()).thenReturn(new ApprovalQueue());
        LoopService service = new LoopService(loop, new LoopSchedulerState(false), publisher, configured());

        RejectResponse response = service.reject("missing");

        assertThat(response.ok()).isFalse();
        assertThat(response.error()).isEqualTo("No pending candidate with id missing");
    }
}
