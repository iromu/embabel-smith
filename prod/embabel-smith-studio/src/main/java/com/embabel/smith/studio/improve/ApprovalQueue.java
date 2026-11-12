/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.embabel.smith.studio.improve;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.embabel.smith.builder.publish.PublishResult;

/**
 * Holds gated candidates awaiting an explicit decision. The default is <em>hold</em>: a
 * gated candidate is queued, not published. Approving a candidate publishes it (via the
 * loop's publisher) and transitions the lifecycle; rejecting discards it. An
 * {@code autoPublish} mode publishes gated candidates immediately, leaving the queue
 * empty.
 *
 * @author Ivan Rodriguez
 */
public final class ApprovalQueue {

	/**
	 * Guards {@link #pending}; every method that reads or writes it is synchronized on
	 * it.
	 */
	private final Map<String, PendingCandidate> pending = new LinkedHashMap<>();

	/**
	 * Queues a gated candidate for approval.
	 * @param candidate the gated candidate to queue
	 * @param autoPublish whether gated candidates are published immediately
	 * @param publisher the loop's publisher used when auto-publishing
	 * @return {@code true} when it was held, {@code false} when it was auto-published
	 * immediately.
	 */
	public synchronized boolean enqueue(PendingCandidate candidate, boolean autoPublish, LoopPublisher publisher) {
		if (autoPublish) {
			publisher.publish(candidate);
			return false;
		}
		this.pending.put(candidate.id(), candidate);
		return true;
	}

	/**
	 * The pending candidate with the given id, or {@code null} if it is not pending.
	 * @param id the candidate id to look up
	 * @return the pending candidate with the given id, or {@code null} if it is not
	 * pending.
	 */
	public synchronized PendingCandidate get(String id) {
		return this.pending.get(id);
	}

	/**
	 * The pending candidates, in enqueue order.
	 * @return the pending candidates, in enqueue order.
	 */
	public synchronized List<PendingCandidate> pending() {
		return List.copyOf(new ArrayList<>(this.pending.values()));
	}

	/**
	 * The number of pending candidates.
	 * @return the number of pending candidates.
	 */
	public synchronized int size() {
		return this.pending.size();
	}

	/**
	 * Approves a pending candidate: publishes it and returns the publish result.
	 * Publishes first and removes only on success, so a failed publish leaves the
	 * candidate pending (recoverable) rather than silently losing it.
	 * @param id the candidate id to approve
	 * @param publisher the loop's publisher used to publish the candidate
	 * @return the result of publishing the approved candidate.
	 * @throws IllegalArgumentException if the candidate is not pending
	 */
	public synchronized PublishResult approve(String id, LoopPublisher publisher) {
		PendingCandidate candidate = this.pending.get(id);
		if (candidate == null) {
			throw new IllegalArgumentException("No pending candidate with id " + id);
		}
		PublishResult result = publisher.publish(candidate);
		this.pending.remove(id);
		return result;
	}

	/**
	 * Rejects a pending candidate (discards it, no publish).
	 * @param id the candidate id to reject
	 * @throws IllegalArgumentException if the candidate is not pending
	 */
	public synchronized void reject(String id) {
		if (this.pending.remove(id) == null) {
			throw new IllegalArgumentException("No pending candidate with id " + id);
		}
	}

	/**
	 * Publishes a gated candidate to the spec source and returns the result. Implemented
	 * by the loop, so the queue stays a pure data structure.
	 */
	@FunctionalInterface
	public interface LoopPublisher {

		PublishResult publish(PendingCandidate candidate);

	}

}
