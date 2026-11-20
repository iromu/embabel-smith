/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.loop;

import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.builder.lifecycle.LifecycleState;
import com.embabel.smith.builder.publish.PublishResult;
import com.embabel.smith.builder.publish.Publisher;
import com.embabel.smith.common.config.ModelEndpointConfig;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.studio.improve.ApprovalQueue;
import com.embabel.smith.studio.improve.ImprovementLoop;
import com.embabel.smith.studio.improve.LoopPublishPolicy;
import com.embabel.smith.studio.improve.PendingCandidate;
import com.embabel.smith.studio.rest.dto.ApproveResponse;
import com.embabel.smith.studio.rest.dto.IterationInfo;
import com.embabel.smith.studio.rest.dto.RejectResponse;
import com.embabel.smith.studio.store.ProcessStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Offline tests for the loop control surface (the only production auto-publish path): a
 * missing model endpoint is reported without running; auto-publish approves and publishes
 * each produced candidate; a publish failure leaves the candidate pending; hold mode
 * queues without publishing; a publish that the loop's push policy refuses is reported
 * and leaves the candidate pending; and approve/reject on an unknown id surface the
 * error. A successful publish also leaves a versioned row in the store, and a publish
 * that did not happen leaves none (change: publish-keeps-versions, task 3.3).
 */
@ExtendWith(MockitoExtension.class)
class LoopServiceTest {

	@Mock
	ImprovementLoop loop;

	@Mock
	Publisher publisher;

	@Mock
	ProcessStore packStore;

	private static ModelEndpointConfig configured() {
		return new ModelEndpointConfig("http://localhost:4000", "key", "m1", "m1");
	}

	private static ModelEndpointConfig missing() {
		return new ModelEndpointConfig("", "", "", "");
	}

	/** The shipped default: pushing off, over a folder improve source. */
	private LoopPublishPolicy folderPolicy() {
		return new LoopPublishPolicy(false, "folder:/tmp/packs", this.packStore);
	}

	/** A git improve source with pushing off — a publish there could not persist. */
	private LoopPublishPolicy gitPolicyWithoutPush() {
		return new LoopPublishPolicy(false, "git:https://example.com/iromu/published-packs.git", this.packStore);
	}

	private static PendingCandidate candidate(String id, String spec) {
		AgentSpec pack = new AgentSpec(new AgentSpecManifest(spec, "1.0.0"),
				GoalStepSpec.exportedGoal(spec, "goal " + spec, "BackgroundMessage"),
				List.of(ActionStepSpec.action("s1", "step s1", List.of("UserInput"), "BackgroundMessage", "do it")),
				List.of());
		LifecycleMetadata lifecycle = new LifecycleMetadata("hash", 1.0, LifecycleState.ACTIVE, "2026-01-01T00:00:00Z");
		return new PendingCandidate(id, spec, pack, lifecycle, 1.0, 0.5, null, List.of(), "rewrote s1");
	}

	@Test
	void triggerWithoutEndpointReportsModelNotConfigured() {
		LoopService service = new LoopService(loop, new LoopSchedulerState(true), publisher, folderPolicy(), missing());

		IterationInfo info = service.trigger();

		assertThat(info.ran()).isFalse();
		assertThat(info.errorCode()).isEqualTo("MODEL_NOT_CONFIGURED");
		verify(loop, never()).runOnce(any());
	}

	@Test
	void autoPublishApprovesAndPublishesProducedCandidates() throws IOException {
		PendingCandidate candidate = candidate("c1", "extract");
		ApprovalQueue queue = new ApprovalQueue();
		queue.enqueue(candidate, false, x -> new PublishResult("/tmp/extract", false)); // held
		when(loop.runOnce(null)).thenReturn(List.of(candidate));
		when(loop.queue()).thenReturn(queue);
		when(publisher.publish(any(), any(), anyBoolean(), anyBoolean()))
			.thenReturn(new PublishResult("/tmp/extract", false));
		LoopService service = new LoopService(loop, new LoopSchedulerState(true), publisher, folderPolicy(),
				configured());

		IterationInfo info = service.trigger();

		assertThat(info.ran()).isTrue();
		assertThat(info.candidates()).isEqualTo(1);
		assertThat(info.publishedSpecs()).containsExactly("extract");
		assertThat(info.queuedSpecs()).isEmpty();
		// Pushing is off by default (smith.studio.loop.push); the folder publisher
		// ignores the
		// flag, so the local publish still happens.
		verify(publisher).publish(any(), any(), anyBoolean(), eq(false));
	}

	@Test
	void aPublishFailureLeavesTheCandidatePending() throws IOException {
		PendingCandidate candidate = candidate("c1", "extract");
		ApprovalQueue queue = new ApprovalQueue();
		queue.enqueue(candidate, false, x -> new PublishResult("/tmp/extract", false));
		when(loop.runOnce(null)).thenReturn(List.of(candidate));
		when(loop.queue()).thenReturn(queue);
		when(publisher.publish(any(), any(), anyBoolean(), anyBoolean())).thenThrow(new IOException("boom"));
		LoopService service = new LoopService(loop, new LoopSchedulerState(true), publisher, folderPolicy(),
				configured());

		IterationInfo info = service.trigger();

		assertThat(info.publishedSpecs()).isEmpty();
		assertThat(info.queuedSpecs()).containsExactly("extract"); // left pending for a
																	// manual decision
		assertThat(queue.size()).isEqualTo(1);
		// A publish that did not happen leaves no row behind.
		verify(this.packStore, never()).savePackWhenConfigured(any());
	}

	/**
	 * The row a loop publish leaves (change: publish-keeps-versions, task 3.3). Fails if
	 * the save-out after a successful publish is removed, and fails if the row is written
	 * at a version other than the one the published pack declares.
	 */
	@Test
	void aLoopPublishRecordsTheCandidateAtItsVersion() throws IOException {
		PendingCandidate candidate = candidate("c1", "extract"); // declares 1.0.0
		ApprovalQueue queue = new ApprovalQueue();
		queue.enqueue(candidate, false, x -> new PublishResult("/tmp/extract", false)); // held
		when(loop.runOnce(null)).thenReturn(List.of(candidate));
		when(loop.queue()).thenReturn(queue);
		when(publisher.publish(any(), any(), anyBoolean(), anyBoolean()))
			.thenReturn(new PublishResult("/tmp/extract", false));
		LoopService service = new LoopService(loop, new LoopSchedulerState(true), publisher, folderPolicy(),
				configured());

		service.trigger();

		ArgumentCaptor<AgentSpec> saved = ArgumentCaptor.forClass(AgentSpec.class);
		verify(this.packStore).savePackWhenConfigured(saved.capture());
		// The pack saved is the one handed to the publisher, so the destination and the
		// row cannot disagree about its version.
		assertThat(saved.getValue()).isSameAs(candidate.candidate());
		assertThat(saved.getValue().version()).isEqualTo("1.0.0");
	}

	@Test
	void holdModeQueuesWithoutPublishing() throws IOException {
		PendingCandidate candidate = candidate("c1", "extract");
		when(loop.runOnce(null)).thenReturn(List.of(candidate));
		LoopService service = new LoopService(loop, new LoopSchedulerState(false), publisher, folderPolicy(),
				configured());

		IterationInfo info = service.trigger();

		assertThat(info.publishedSpecs()).isEmpty();
		assertThat(info.queuedSpecs()).containsExactly("extract");
		verify(publisher, never()).publish(any(), any(), anyBoolean(), anyBoolean());
	}

	@Test
	void approvingAGitCandidateWithoutPushingIsRefusedAndStaysPending() throws IOException {
		PendingCandidate candidate = candidate("c1", "extract");
		ApprovalQueue queue = new ApprovalQueue();
		queue.enqueue(candidate, false, x -> new PublishResult("/tmp/extract", false)); // held
		when(loop.queue()).thenReturn(queue);
		LoopService service = new LoopService(loop, new LoopSchedulerState(false), publisher, gitPolicyWithoutPush(),
				configured());

		ApproveResponse response = service.approve("c1");

		assertThat(response.ok()).isFalse();
		assertThat(response.error()).contains("smith.studio.loop.push").contains("was not published");
		assertThat(response.location()).isNull();
		assertThat(service.candidates()).extracting(PendingCandidate::id).containsExactly("c1");
		verify(publisher, never()).publish(any(), any(), anyBoolean(), anyBoolean());
		verify(this.packStore, never()).savePackWhenConfigured(any()); // nothing to
																		// record
	}

	@Test
	void anAutoPublishIterationReportsThePushRefusalInsteadOfAPublish() throws IOException {
		PendingCandidate candidate = candidate("c1", "extract");
		ApprovalQueue queue = new ApprovalQueue();
		queue.enqueue(candidate, false, x -> new PublishResult("/tmp/extract", false)); // held
		when(loop.runOnce(null)).thenReturn(List.of(candidate));
		when(loop.queue()).thenReturn(queue);
		LoopService service = new LoopService(loop, new LoopSchedulerState(true), publisher, gitPolicyWithoutPush(),
				configured());

		IterationInfo info = service.trigger();

		assertThat(info.ran()).isTrue();
		assertThat(info.publishedSpecs()).isEmpty(); // nothing was written
		assertThat(info.queuedSpecs()).containsExactly("extract");
		assertThat(info.errorCode()).isEqualTo("PUSH_DISABLED");
		assertThat(info.error()).contains("smith.studio.loop.push");
		verify(publisher, never()).publish(any(), any(), anyBoolean(), anyBoolean());
	}

	@Test
	void approvingAnUnknownIdReportsTheError() {
		when(loop.queue()).thenReturn(new ApprovalQueue());
		LoopService service = new LoopService(loop, new LoopSchedulerState(false), publisher, folderPolicy(),
				configured());

		ApproveResponse response = service.approve("missing");

		assertThat(response.ok()).isFalse();
		assertThat(response.error()).isEqualTo("No pending candidate with id missing");
	}

	@Test
	void rejectingAnUnknownIdReportsTheError() {
		when(loop.queue()).thenReturn(new ApprovalQueue());
		LoopService service = new LoopService(loop, new LoopSchedulerState(false), publisher, folderPolicy(),
				configured());

		RejectResponse response = service.reject("missing");

		assertThat(response.ok()).isFalse();
		assertThat(response.error()).isEqualTo("No pending candidate with id missing");
	}

}
