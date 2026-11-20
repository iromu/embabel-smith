/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.remote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline tests of the bridge client against {@link StubSidecar}, an in-process server
 * that speaks the three-endpoint contract. Every case names the response shape that
 * decides the state, because the rule under test is that reachability comes from the
 * answer and not from the absence of an exception: a 200 whose body is not a catalog is
 * unreachable, and a refusal keeps the sidecar's own status and body instead of becoming
 * a result the product composed.
 */
class RemoteActionBridgeTest {

	private static final String USER_INPUT = "com.embabel.agent.domain.io.UserInput";

	/** One action this pane can run, one it cannot, both named by the sidecar. */
	private static final String TWO_ACTION_CATALOG = """
			[
			  { "name": "remoteDigest",
			    "description": "Digest the input text on the sidecar",
			    "inputs":  [ { "name": "userInput", "type": "%s" } ],
			    "outputs": [ { "name": "it", "type": "com.embabel.agent.spec.model.BackgroundMessage" } ] },
			  { "name": "compareBriefs",
			    "description": "Needs a second input this pane cannot supply",
			    "inputs":  [ { "name": "userInput", "type": "%s" }, { "name": "brief", "type": "TopicBrief" } ] }
			]
			""".formatted(USER_INPUT, USER_INPUT);

	private static RemoteActionBridge bridge(StubSidecar sidecar) {
		return new RemoteActionBridge(new RemoteBridge("digest", sidecar.baseUrl()));
	}

	@Test
	void aCatalogAnswerMakesTheBridgeReachableWithExactlyTheAdvertisedActions() throws Exception {
		try (StubSidecar sidecar = StubSidecar.start()) {
			sidecar.respondToCatalog(200, TWO_ACTION_CATALOG);

			BridgeState state = bridge(sidecar).probe();

			assertThat(state.reachable()).isTrue();
			assertThat(state.reason()).isNull();
			assertThat(state.name()).isEqualTo("digest");
			assertThat(state.urlProbed()).isEqualTo(sidecar.baseUrl() + "/api/v1/actions");
			// The names are the sidecar's; the product adds none and renames none.
			assertThat(state.actions()).extracting(AdvertisedAction::name)
				.containsExactly("remoteDigest", "compareBriefs");
			assertThat(state.actions().get(0).description()).isEqualTo("Digest the input text on the sidecar");
			assertThat(state.actions().get(1).inputTypeNames()).containsExactly(USER_INPUT, "TopicBrief");
			assertThat(sidecar.requestLines()).containsExactly("GET /api/v1/actions");
		}
	}

	@Test
	void onlyASingleUserInputActionIsRunnableFromTheTextInputPane() throws Exception {
		try (StubSidecar sidecar = StubSidecar.start()) {
			sidecar.respondToCatalog(200, TWO_ACTION_CATALOG);

			BridgeState state = bridge(sidecar).probe();

			assertThat(state.actions().get(0).singleUserInput()).isTrue();
			assertThat(state.actions().get(1).singleUserInput())
				.as("an action declaring a second input has no field to put it in")
				.isFalse();
		}
	}

	@Test
	void anEmptyCatalogIsReachableWithNothingToOffer() throws Exception {
		try (StubSidecar sidecar = StubSidecar.start()) {
			sidecar.respondToCatalog(200, "[]");

			BridgeState state = bridge(sidecar).probe();

			assertThat(state.reachable()).isTrue();
			assertThat(state.actions()).isEmpty();
		}
	}

	@Test
	void aServerErrorIsUnreachableCarryingTheStatusItAnsweredWith() throws Exception {
		try (StubSidecar sidecar = StubSidecar.start()) {
			sidecar.respondToCatalog(500, "{\"error\":\"boom\"}");

			BridgeState state = bridge(sidecar).probe();

			assertThat(state.reachable()).isFalse();
			assertThat(state.reason()).contains("500");
			assertThat(state.actions()).isEmpty();
		}
	}

	@Test
	void aSuccessThatIsNotACatalogIsUnreachableRatherThanReachable() throws Exception {
		// The case a client decides from the exception alone gets wrong: a process is
		// answering, so
		// nothing throws, yet it did not serve a catalog and must not be reported as
		// working.
		try (StubSidecar sidecar = StubSidecar.start()) {
			sidecar.respondToCatalog(200, "{\"actions\":[{\"name\":\"remoteDigest\"}]}");

			BridgeState state = bridge(sidecar).probe();

			assertThat(state.reachable()).as("a 200 with an object body is not a catalog").isFalse();
			assertThat(state.reason()).contains("not a JSON array");
		}
	}

	@Test
	void aRefusedConnectionIsUnreachableWithTheUrlThatWasProbed() throws Exception {
		String dead = StubSidecar.closedBaseUrl();

		BridgeState state = new RemoteActionBridge(new RemoteBridge("gone", dead)).probe();

		assertThat(state.reachable()).isFalse();
		assertThat(state.urlProbed()).isEqualTo(dead + "/api/v1/actions");
		assertThat(state.reason()).contains("ConnectException");
	}

	@Test
	void anActionRunReturnsTheContentTheSidecarProduced() throws Exception {
		try (StubSidecar sidecar = StubSidecar.start()) {
			sidecar.respondToExecute(200,
					"{\"content\":\"Digested on the stub: 3 words, 15 chars\",\"timestamp\":\"2026-11-19T00:00:00Z\"}");

			ExecuteResult result = bridge(sidecar).execute("remoteDigest", "the input text");

			assertThat(result.success()).isTrue();
			assertThat(result.output()).isEqualTo("Digested on the stub: 3 words, 15 chars");
			assertThat(result.status()).isNull();
			assertThat(sidecar.requestLines()).containsExactly("POST /api/v1/actions/execute");
			assertThat(body(sidecar.lastExecuteRequestBody()).path("action_name").asText()).isEqualTo("remoteDigest");
			assertThat(body(sidecar.lastExecuteRequestBody()).path("parameters")
				.path("userInput")
				.path("content")
				.asText()).isEqualTo("the input text");
		}
	}

	@Test
	void quotesAndNewlinesInTheInputStayInsideTheContentTheyWereSentAs() throws Exception {
		try (StubSidecar sidecar = StubSidecar.start()) {
			sidecar.respondToExecute(200, "{\"content\":\"ok\"}");

			bridge(sidecar).execute("remoteDigest", "he said \"hi\"\nthen left");

			assertThat(body(sidecar.lastExecuteRequestBody()).path("parameters")
				.path("userInput")
				.path("content")
				.asText()).isEqualTo("he said \"hi\"\nthen left");
		}
	}

	@Test
	void aRefusedActionKeepsTheSidecarStatusAndBodyInsteadOfInventingAResult() throws Exception {
		try (StubSidecar sidecar = StubSidecar.start()) {
			sidecar.respondToExecute(400, "{\"error\":\"unknown action: nope\"}");

			ExecuteResult result = bridge(sidecar).execute("nope", "text");

			assertThat(result.success()).isFalse();
			assertThat(result.output()).as("no result is composed for an action that never ran").isNull();
			assertThat(result.status()).isEqualTo(400);
			assertThat(result.sidecarBody()).isEqualTo("{\"error\":\"unknown action: nope\"}");
		}
	}

	@Test
	void anActionRunAgainstADeadBridgeIsUnreachableNotAResult() throws Exception {
		String dead = StubSidecar.closedBaseUrl();

		ExecuteResult result = new RemoteActionBridge(new RemoteBridge("gone", dead)).execute("remoteDigest", "text");

		assertThat(result.success()).isFalse();
		assertThat(result.output()).isNull();
		assertThat(result.status()).as("no sidecar answered, so there is no status to report").isNull();
		assertThat(result.urlProbed()).isEqualTo(dead + "/api/v1/actions/execute");
		assertThat(result.error()).contains("ConnectException");
	}

	private static JsonNode body(String json) throws Exception {
		return new ObjectMapper().readTree(json);
	}

}
