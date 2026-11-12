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

package com.embabel.smith.studio.loop;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import com.embabel.smith.builder.publish.PublishResult;
import com.embabel.smith.builder.publish.Publisher;
import com.embabel.smith.common.config.ModelEndpointConfig;
import com.embabel.smith.studio.improve.ImprovementLoop;
import com.embabel.smith.studio.improve.PendingCandidate;
import com.embabel.smith.studio.rest.dto.ApproveResponse;
import com.embabel.smith.studio.rest.dto.IterationInfo;
import com.embabel.smith.studio.rest.dto.LoopStatus;
import com.embabel.smith.studio.rest.dto.RejectResponse;

import org.springframework.stereotype.Service;

/**
 * The studio's control surface over the self-improvement loop (change 06): runs
 * iterations (on demand from the UI or from the interval {@link LoopScheduler}),
 * starts/stops the schedule, toggles auto-publish, and approves/rejects pending
 * candidates.
 *
 * <p>
 * The loop is always constructed in <em>hold</em> mode, so every gated candidate lands in
 * the approval queue. Auto-publish is implemented at this layer: when the flag is on,
 * each iteration immediately approves (publishes) the candidates it produced, so they
 * never sit pending. This keeps the change-05 loop untouched while making the toggle
 * effective at runtime.
 *
 * @author Ivan Rodriguez
 */
@Service
public class LoopService {

	private final ImprovementLoop loop;

	private final LoopSchedulerState state;

	private final Publisher publisher;

	private final ModelEndpointConfig endpoint;

	public LoopService(ImprovementLoop loop, LoopSchedulerState state, Publisher publisher,
			ModelEndpointConfig endpoint) {
		this.loop = loop;
		this.state = state;
		this.publisher = publisher;
		this.endpoint = endpoint;
	}

	/**
	 * Runs one iteration: the loop pass over the improve source, then — when auto-publish
	 * is on — immediately approves every candidate it produced. A missing model endpoint
	 * is reported (structured {@code MODEL_NOT_CONFIGURED}) without running, since the
	 * iteration's re-eval spends model calls.
	 * @return the iteration outcome.
	 */
	public IterationInfo trigger() {
		String configError = this.endpoint.configError();
		if (configError != null) {
			return new IterationInfo(false, 0, List.of(), List.of(), this.state.autoPublish(),
					"MODEL_NOT_CONFIGURED", configError);
		}
		List<PendingCandidate> produced = this.loop.runOnce();
		List<String> published = new ArrayList<>();
		List<String> queued = new ArrayList<>();
		if (this.state.autoPublish()) {
			for (PendingCandidate candidate : produced) {
				try {
					this.loop.queue().approve(candidate.id(), this::publish);
					published.add(candidate.spec());
				}
				catch (RuntimeException ex) {
					queued.add(candidate.spec()); // publish failed; leave it pending for
													// a manual decision
				}
			}
		}
		else {
			for (PendingCandidate candidate : produced) {
				queued.add(candidate.spec());
			}
		}
		return new IterationInfo(true, produced.size(), published, queued, this.state.autoPublish(), null, null);
	}

	/**
	 * Starts the interval schedule.
	 * @return the new schedule status.
	 */
	public LoopStatus start() {
		this.state.start();
		return status();
	}

	/**
	 * Stops the interval schedule.
	 * @return the new schedule status.
	 */
	public LoopStatus stop() {
		this.state.stop();
		return status();
	}

	/**
	 * The current schedule + auto-publish status.
	 * @return the current schedule + auto-publish status.
	 */
	public LoopStatus status() {
		return new LoopStatus(this.state.running(), this.state.autoPublish());
	}

	/**
	 * The pending candidates awaiting a decision.
	 * @return the pending candidates awaiting a decision.
	 */
	public List<PendingCandidate> candidates() {
		return this.loop.queue().pending();
	}

	/**
	 * Sets the auto-publish mode.
	 * @param autoPublish the auto-publish mode to set.
	 * @return the new schedule status.
	 */
	public LoopStatus setAutoPublish(boolean autoPublish) {
		this.state.setAutoPublish(autoPublish);
		return status();
	}

	/**
	 * Approves (publishes) a pending candidate.
	 * @param id the pending candidate id to approve.
	 * @return the approval outcome.
	 */
	public ApproveResponse approve(String id) {
		try {
			PublishResult result = this.loop.queue().approve(id, this::publish);
			return new ApproveResponse(true, result.location().toString(), null);
		}
		catch (IllegalArgumentException ex) {
			return new ApproveResponse(false, null, "No pending candidate with id " + id);
		}
	}

	/**
	 * Rejects (discards) a pending candidate.
	 * @param id the pending candidate id to reject.
	 * @return the rejection outcome.
	 */
	public RejectResponse reject(String id) {
		try {
			this.loop.queue().reject(id);
			return new RejectResponse(true, null);
		}
		catch (IllegalArgumentException ex) {
			return new RejectResponse(false, "No pending candidate with id " + id);
		}
	}

	/**
	 * Publishes a candidate through the shared publisher (the loop's write path).
	 * push=true: an approved/auto-published candidate must persist into the spec source,
	 * so a git source is pushed (the folder publisher ignores the flag).
	 * @param candidate the candidate to publish.
	 * @return the publish result.
	 */
	private PublishResult publish(PendingCandidate candidate) {
		try {
			return this.publisher.publish(candidate.candidate(), candidate.proposedLifecycle(), true, true);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("cannot publish candidate for " + candidate.spec(), ex);
		}
	}

}
