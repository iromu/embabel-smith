package com.embabel.smith.studio.improve;

import com.embabel.smith.builder.publish.PublishResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds gated candidates awaiting an explicit decision. The default is <em>hold</em>: a gated
 * candidate is queued, not published. Approving a candidate publishes it (via the loop's publisher)
 * and transitions the lifecycle; rejecting discards it. An {@code autoPublish} mode publishes gated
 * candidates immediately, leaving the queue empty.
 */
public final class ApprovalQueue {

    private final Map<String, PendingCandidate> pending = new LinkedHashMap<>();

    /**
     * Queues a gated candidate for approval.
     *
     * @return {@code true} when it was held, {@code false} when it was auto-published immediately
     */
    public boolean enqueue(PendingCandidate candidate, boolean autoPublish, LoopPublisher publisher) {
        if (autoPublish) {
            publisher.publish(candidate);
            return false;
        }
        pending.put(candidate.id(), candidate);
        return true;
    }

    /** The pending candidate with the given id, or {@code null} if it is not pending. */
    public PendingCandidate get(String id) {
        return pending.get(id);
    }

    /** The pending candidates, in enqueue order. */
    public List<PendingCandidate> pending() {
        return List.copyOf(new ArrayList<>(pending.values()));
    }

    public int size() {
        return pending.size();
    }

    /**
     * Approves a pending candidate: publishes it and returns the publish result.
     *
     * @throws IllegalArgumentException if the candidate is not pending
     */
    public PublishResult approve(String id, LoopPublisher publisher) {
        PendingCandidate candidate = pending.remove(id);
        if (candidate == null) {
            throw new IllegalArgumentException("No pending candidate with id " + id);
        }
        return publisher.publish(candidate);
    }

    /**
     * Rejects a pending candidate (discards it, no publish).
     *
     * @throws IllegalArgumentException if the candidate is not pending
     */
    public void reject(String id) {
        if (pending.remove(id) == null) {
            throw new IllegalArgumentException("No pending candidate with id " + id);
        }
    }

    /**
     * Publishes a gated candidate to the skill source and returns the result. Implemented by the
     * loop, so the queue stays a pure data structure.
     */
    @FunctionalInterface
    public interface LoopPublisher {
        PublishResult publish(PendingCandidate candidate);
    }
}
