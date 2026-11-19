/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.remote;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The bridge value shapes, read back component by component. Each factory is called with
 * a different value in every slot it can fill, so a factory that wires a component into
 * the wrong position — a name where the URL belongs, an unreachable reason that lands in
 * the actions — fails here rather than in a pane.
 */
class RemoteBridgeShapesTest {

	private static final RemoteBridge BRIDGE = new RemoteBridge("digest", "http://127.0.0.1:18090");

	private static final AdvertisedAction DIGEST = new AdvertisedAction("remoteDigest", "digest the text",
			List.of(AdvertisedAction.USER_INPUT));

	@Test
	void theDescriptorCarriesTheConfiguredNameAndUrl() {
		assertThat(BRIDGE.name()).isEqualTo("digest");
		assertThat(BRIDGE.baseUrl()).isEqualTo("http://127.0.0.1:18090");
	}

	@Test
	void anAdvertisedActionCarriesWhatTheSidecarPublished() {
		assertThat(DIGEST.name()).isEqualTo("remoteDigest");
		assertThat(DIGEST.description()).isEqualTo("digest the text");
		assertThat(DIGEST.inputTypeNames()).containsExactly(AdvertisedAction.USER_INPUT);
	}

	@Test
	void aReachableStateKeepsTheBridgeTheProbedUrlAndTheActionsApart() {
		BridgeState state = BridgeState.reachable(BRIDGE, "http://127.0.0.1:18090/api/v1/actions", List.of(DIGEST));

		assertThat(state.name()).isEqualTo("digest");
		assertThat(state.baseUrl()).isEqualTo("http://127.0.0.1:18090");
		assertThat(state.urlProbed()).isEqualTo("http://127.0.0.1:18090/api/v1/actions");
		assertThat(state.reachable()).isTrue();
		assertThat(state.reason()).isNull();
		assertThat(state.actions()).containsExactly(DIGEST);
	}

	@Test
	void anUnreachableStateKeepsTheReasonAndOffersNoActions() {
		BridgeState state = BridgeState.unreachable(BRIDGE, "http://127.0.0.1:18090/api/v1/actions",
				"answered HTTP 503");

		assertThat(state.name()).isEqualTo("digest");
		assertThat(state.urlProbed()).isEqualTo("http://127.0.0.1:18090/api/v1/actions");
		assertThat(state.reachable()).isFalse();
		assertThat(state.reason()).isEqualTo("answered HTTP 503");
		assertThat(state.actions()).isEmpty();
	}

	@Test
	void theReachableStateDoesNotAliasTheCallerActionList() {
		List<AdvertisedAction> mutable = new ArrayList<>(List.of(DIGEST));

		BridgeState state = BridgeState.reachable(BRIDGE, "u", mutable);
		mutable.clear();

		assertThat(state.actions()).as("the state is a snapshot, not a live view of the caller's list")
			.containsExactly(DIGEST);
	}

	@Test
	void theExecutedResultCarriesOnlyTheSidecarsContent() {
		ExecuteResult result = ExecuteResult.executed("http://x/api/v1/actions/execute", "Digested: 3 words");

		assertThat(result.success()).isTrue();
		assertThat(result.urlProbed()).isEqualTo("http://x/api/v1/actions/execute");
		assertThat(result.output()).isEqualTo("Digested: 3 words");
		assertThat(result.status()).isNull();
		assertThat(result.sidecarBody()).isNull();
		assertThat(result.error()).isNull();
	}

	@Test
	void theRefusedResultCarriesTheStatusAndBodyAndNoOutput() {
		ExecuteResult result = ExecuteResult.refused("http://x/api/v1/actions/execute", 400, "{\"error\":\"nope\"}");

		assertThat(result.success()).isFalse();
		assertThat(result.status()).isEqualTo(400);
		assertThat(result.sidecarBody()).isEqualTo("{\"error\":\"nope\"}");
		assertThat(result.output()).isNull();
		assertThat(result.error()).isNull();
	}

	@Test
	void theUnreachableResultCarriesTheFailureAndNeitherStatusNorBody() {
		ExecuteResult result = ExecuteResult.unreachable("http://x/api/v1/actions/execute", "Connection refused");

		assertThat(result.success()).isFalse();
		assertThat(result.error()).isEqualTo("Connection refused");
		assertThat(result.status()).isNull();
		assertThat(result.sidecarBody()).isNull();
		assertThat(result.output()).isNull();
	}

}
