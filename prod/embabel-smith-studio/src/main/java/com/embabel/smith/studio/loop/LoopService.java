/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.loop;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import com.embabel.smith.builder.improve.ImprovementLoop;
import com.embabel.smith.builder.improve.LoopPublishPolicy;
import com.embabel.smith.builder.improve.LoopPushDisabledException;
import com.embabel.smith.builder.improve.PendingCandidate;
import com.embabel.smith.builder.publish.PublishResult;
import com.embabel.smith.builder.publish.Publisher;
import com.embabel.smith.common.config.ModelEndpointConfig;
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
 * <p>
 * Publishing obeys {@link LoopPublishPolicy}: pushing is off by default, and a publish
 * that could not persist (a {@code git:} improve source with pushing off) is refused with
 * the setting named, leaving the candidate pending.
 *
 * @author Ivan Rodriguez
 */
@Service
public class LoopService {

	/**
	 * The iteration error code when a publish was refused because pushing is disabled.
	 */
	static final String PUSH_DISABLED = "PUSH_DISABLED";

	private final ImprovementLoop loop;

	private final LoopSchedulerState state;

	private final Publisher publisher;

	private final LoopPublishPolicy publishPolicy;

	private final ModelEndpointConfig endpoint;

	public LoopService(ImprovementLoop loop, LoopSchedulerState state, Publisher publisher,
			LoopPublishPolicy publishPolicy, ModelEndpointConfig endpoint) {
		this.loop = loop;
		this.state = state;
		this.publisher = publisher;
		this.publishPolicy = publishPolicy;
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
		return trigger(null);
	}

	/**
	 * As {@link #trigger()}, running the whole iteration — rebuild, eval, refine — on one
	 * named LLM.
	 * @param model the LLM the iteration runs on, or empty for the configured default.
	 * @return the iteration outcome.
	 */
	public IterationInfo trigger(String model) {
		String configError = this.endpoint.configError();
		if (configError != null) {
			return new IterationInfo(false, 0, List.of(), List.of(), this.state.autoPublish(), "MODEL_NOT_CONFIGURED",
					configError);
		}
		List<PendingCandidate> produced = this.loop.runOnce(model);
		List<String> published = new ArrayList<>();
		List<String> queued = new ArrayList<>();
		String refusal = null;
		if (this.state.autoPublish()) {
			for (PendingCandidate candidate : produced) {
				try {
					this.loop.queue().approve(candidate.id(), this::publish);
					published.add(candidate.spec());
				}
				catch (LoopPushDisabledException ex) {
					// Nothing was written: report why, and leave the candidate pending.
					queued.add(candidate.spec());
					refusal = ex.getMessage();
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
		return new IterationInfo(true, produced.size(), published, queued, this.state.autoPublish(),
				(refusal != null) ? PUSH_DISABLED : null, refusal);
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
	 * Approves (publishes) a pending candidate. A publish that the policy refuses is
	 * reported through the failure channel and the candidate stays pending.
	 * @param id the pending candidate id to approve.
	 * @return the approval outcome.
	 */
	public ApproveResponse approve(String id) {
		try {
			PublishResult result = this.loop.queue().approve(id, this::publish);
			return new ApproveResponse(true, result.location(), null);
		}
		catch (LoopPushDisabledException ex) {
			return new ApproveResponse(false, null, ex.getMessage());
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
	 * Publishes a candidate through the shared publisher under the loop's publish policy
	 * (push is configured, never forced; a publish that could not persist is refused).
	 * @param candidate the candidate to publish.
	 * @return the publish result.
	 */
	private PublishResult publish(PendingCandidate candidate) {
		try {
			return this.publishPolicy.publish(this.publisher, candidate);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("cannot publish candidate for " + candidate.spec(), ex);
		}
	}

}
