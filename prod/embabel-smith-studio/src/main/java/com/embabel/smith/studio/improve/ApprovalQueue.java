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

    /** Guards {@link #pending}; every method that reads or writes it is synchronized on it. */
    private final Map<String, PendingCandidate> pending = new LinkedHashMap<>();

    /**
     * Queues a gated candidate for approval.
     *
     * @return {@code true} when it was held, {@code false} when it was auto-published immediately
     */
    public synchronized boolean enqueue(PendingCandidate candidate, boolean autoPublish, LoopPublisher publisher) {
        if (autoPublish) {
            publisher.publish(candidate);
            return false;
        }
        pending.put(candidate.id(), candidate);
        return true;
    }

    /** The pending candidate with the given id, or {@code null} if it is not pending. */
    public synchronized PendingCandidate get(String id) {
        return pending.get(id);
    }

    /** The pending candidates, in enqueue order. */
    public synchronized List<PendingCandidate> pending() {
        return List.copyOf(new ArrayList<>(pending.values()));
    }

    public synchronized int size() {
        return pending.size();
    }

    /**
     * Approves a pending candidate: publishes it and returns the publish result. Publishes first and
     * removes only on success, so a failed publish leaves the candidate pending (recoverable) rather
     * than silently losing it.
     *
     * @throws IllegalArgumentException if the candidate is not pending
     */
    public synchronized PublishResult approve(String id, LoopPublisher publisher) {
        PendingCandidate candidate = pending.get(id);
        if (candidate == null) {
            throw new IllegalArgumentException("No pending candidate with id " + id);
        }
        PublishResult result = publisher.publish(candidate);
        pending.remove(id);
        return result;
    }

    /**
     * Rejects a pending candidate (discards it, no publish).
     *
     * @throws IllegalArgumentException if the candidate is not pending
     */
    public synchronized void reject(String id) {
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
