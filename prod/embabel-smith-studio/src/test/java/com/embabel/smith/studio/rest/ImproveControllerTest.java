package com.embabel.smith.studio.rest;

import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.builder.lifecycle.LifecycleState;
import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.studio.improve.PendingCandidate;
import com.embabel.smith.studio.loop.LoopService;
import com.embabel.smith.studio.rest.dto.ApproveResponse;
import com.embabel.smith.studio.rest.dto.AutoPublishRequest;
import com.embabel.smith.studio.rest.dto.CandidateInfo;
import com.embabel.smith.studio.rest.dto.IterationInfo;
import com.embabel.smith.studio.rest.dto.LoopStatus;
import com.embabel.smith.studio.rest.dto.RejectResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Offline tests for the Auto-improve surface: delegation from the controller to the LoopService. */
@ExtendWith(MockitoExtension.class)
class ImproveControllerTest {

    @Mock
    LoopService loopService;

    ImproveController controller;

    @BeforeEach
    void setUp() {
        controller = new ImproveController(loopService);
    }

    @Test
    void listsPendingCandidates() {
        when(loopService.candidates()).thenReturn(List.of(candidate()));
        List<CandidateInfo> candidates = controller.candidates();
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).id()).isEqualTo("summarizer-1");
        assertThat(candidates.get(0).skill()).isEqualTo("summarizer");
        assertThat(candidates.get(0).proposedState()).isEqualTo("ACTIVE");
    }

    @Test
    void approvesCandidate() {
        when(loopService.approve("summarizer-1")).thenReturn(new ApproveResponse(true, "/tmp/out", null));
        ApproveResponse response = controller.approve("summarizer-1");
        assertThat(response.ok()).isTrue();
        assertThat(response.location()).isEqualTo("/tmp/out");
        verify(loopService).approve("summarizer-1");
    }

    @Test
    void rejectsCandidate() {
        when(loopService.reject("summarizer-1")).thenReturn(new RejectResponse(true, null));
        RejectResponse response = controller.reject("summarizer-1");
        assertThat(response.ok()).isTrue();
        verify(loopService).reject("summarizer-1");
    }

    @Test
    void triggersAnIteration() {
        when(loopService.trigger()).thenReturn(
                new IterationInfo(true, 1, List.of("summarizer"), List.of(), false, null, null));
        IterationInfo info = controller.trigger();
        assertThat(info.ran()).isTrue();
        assertThat(info.candidates()).isEqualTo(1);
        assertThat(info.publishedSkills()).contains("summarizer");
    }

    @Test
    void startStopAndStatus() {
        when(loopService.start()).thenReturn(new LoopStatus(true, false));
        when(loopService.stop()).thenReturn(new LoopStatus(false, false));
        when(loopService.status()).thenReturn(new LoopStatus(false, false));
        assertThat(controller.start().running()).isTrue();
        assertThat(controller.stop().running()).isFalse();
        assertThat(controller.status().running()).isFalse();
    }

    @Test
    void getsAndSetsAutoPublish() {
        when(loopService.status()).thenReturn(new LoopStatus(false, false));
        when(loopService.setAutoPublish(true)).thenReturn(new LoopStatus(false, true));
        assertThat(controller.autoPublish().autoPublish()).isFalse();
        assertThat(controller.setAutoPublish(new AutoPublishRequest(true)).autoPublish()).isTrue();
        verify(loopService).setAutoPublish(true);
    }

    private static PendingCandidate candidate() {
        SkillPack pack = RestTestFixtures.pack("summarizer", "summarize");
        LifecycleMetadata proposed =
                new LifecycleMetadata("hash", 1.0, LifecycleState.ACTIVE, "now");
        return new PendingCandidate("summarizer-1", "summarizer", pack, proposed, 1.0, 0.5, null,
                List.of(), "rewrote prompts of: summarize");
    }
}
