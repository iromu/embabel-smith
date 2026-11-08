package com.embabel.smith.studio.improve;

import com.embabel.smith.builder.publish.PublishResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalQueueTest {

    @Test
    void holdsByDefault() {
        ApprovalQueue queue = new ApprovalQueue();
        PendingCandidate candidate = TestFixtures.candidate("c1", "extract");

        boolean held = queue.enqueue(candidate, false, c -> {
            throw new AssertionError("should not publish in hold mode");
        });

        assertThat(held).isTrue();
        assertThat(queue.size()).isEqualTo(1);
        assertThat(queue.get("c1")).isEqualTo(candidate);
    }

    @Test
    void approvePublishes() {
        ApprovalQueue queue = new ApprovalQueue();
        PendingCandidate candidate = TestFixtures.candidate("c1", "extract");
        queue.enqueue(candidate, false, c -> null);

        List<PendingCandidate> published = new ArrayList<>();
        PublishResult result = queue.approve("c1", c -> {
            published.add(c);
            return new PublishResult(Path.of("/tmp/extract"), false);
        });

        assertThat(published).containsExactly(candidate);
        assertThat(result.location()).isEqualTo(Path.of("/tmp/extract"));
        assertThat(queue.size()).isZero();
    }

    @Test
    void rejectDiscardsWithoutPublishing() {
        ApprovalQueue queue = new ApprovalQueue();
        PendingCandidate candidate = TestFixtures.candidate("c1", "extract");
        queue.enqueue(candidate, false, c -> null);

        queue.reject("c1");

        assertThat(queue.size()).isZero();
    }

    @Test
    void autoPublishesImmediately() {
        ApprovalQueue queue = new ApprovalQueue();
        PendingCandidate candidate = TestFixtures.candidate("c1", "extract");
        List<PendingCandidate> published = new ArrayList<>();

        boolean held = queue.enqueue(candidate, true, c -> {
            published.add(c);
            return new PublishResult(Path.of("/tmp/extract"), false);
        });

        assertThat(held).isFalse();
        assertThat(published).containsExactly(candidate);
        assertThat(queue.size()).isZero();
    }

    @Test
    void approvingUnknownIdFails() {
        ApprovalQueue queue = new ApprovalQueue();
        assertThatThrownBy(() -> queue.approve("missing", c -> null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failedPublishLeavesCandidatePending() {
        ApprovalQueue queue = new ApprovalQueue();
        PendingCandidate candidate = TestFixtures.candidate("c1", "extract");
        queue.enqueue(candidate, false, c -> null);

        RuntimeException boom = new RuntimeException("publish failed");
        assertThatThrownBy(() -> queue.approve("c1", c -> {
            throw boom;
        })).isSameAs(boom);

        // The candidate survives the failed publish: it is still pending and can be retried.
        assertThat(queue.size()).isEqualTo(1);
        assertThat(queue.get("c1")).isEqualTo(candidate);
        assertThat(queue.pending()).containsExactly(candidate);
    }
}
