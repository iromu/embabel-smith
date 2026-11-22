/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest;

import com.embabel.smith.studio.rest.dto.RemoteExecuteRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Offline MockMvc tests for the Remote endpoints, against {@link StubSidecar} — an
 * in-process sidecar, never a process the build launched. The JSON is read through
 * Jackson rather than the json-path matchers so a failure names the DTO field that moved,
 * which is also what keeps these field names honest against the frontend mirror.
 *
 * <p>
 * The load-bearing assertion is on the stub's request count: a refusal has to send
 * nothing, and the only way to show that is to count what arrived. Each test that expects
 * a refusal first performs a request that <em>is</em> allowed, so a stub that counted
 * nothing at all could not pass it by accident.
 */
class RemoteControllerTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final String TWO_ACTION_CATALOG = """
			[
			  { "name": "remoteDigest",
			    "description": "Digest the input text on the sidecar",
			    "inputs":  [ { "name": "userInput", "type": "com.embabel.agent.domain.io.UserInput" } ] },
			  { "name": "compareBriefs",
			    "description": "Needs an input this pane has no field for",
			    "inputs":  [ { "name": "userInput", "type": "com.embabel.agent.domain.io.UserInput" },
			                 { "name": "brief", "type": "TopicBrief" } ] }
			]
			""";

	private static MockMvc mvc(String configuredBridges) {
		return MockMvcBuilders.standaloneSetup(new RemoteController(configuredBridges, "")).build();
	}

	private static MockMvc mvc(String configuredBridges, String mcpEndpoint) {
		return MockMvcBuilders.standaloneSetup(new RemoteController(configuredBridges, mcpEndpoint)).build();
	}

	private static JsonNode bridges(MockMvc mvc) throws Exception {
		ResultActions performed = mvc.perform(get("/api/remote/bridges")).andExpect(status().isOk());
		return MAPPER.readTree(performed.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
	}

	private static JsonNode execute(MockMvc mvc, RemoteExecuteRequest request) throws Exception {
		ResultActions performed = mvc
			.perform(post("/api/remote/execute").contentType(MediaType.APPLICATION_JSON)
				.content(MAPPER.writeValueAsString(request)))
			.andExpect(status().isOk());
		return MAPPER.readTree(performed.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
	}

	@Test
	void listsAReachableBridgeWithExactlyTheTwoActionsItAdvertises() throws Exception {
		try (StubSidecar sidecar = StubSidecar.start()) {
			sidecar.respondToCatalog(200, TWO_ACTION_CATALOG);

			JsonNode body = bridges(mvc("digest@" + sidecar.baseUrl()));

			assertThat(body.path("bridges")).hasSize(1);
			JsonNode bridge = body.path("bridges").get(0);
			assertThat(bridge.path("name").asText()).isEqualTo("digest");
			assertThat(bridge.path("baseUrl").asText()).isEqualTo(sidecar.baseUrl());
			assertThat(bridge.path("urlProbed").asText()).isEqualTo(sidecar.baseUrl() + "/api/v1/actions");
			assertThat(bridge.path("reachable").asBoolean()).isTrue();
			assertThat(bridge.path("reason").isNull()).isTrue();
			// The names come from the sidecar; the product contributes none.
			assertThat(bridge.path("actions").get(0).path("name").asText()).isEqualTo("remoteDigest");
			assertThat(bridge.path("actions").get(1).path("name").asText()).isEqualTo("compareBriefs");
			assertThat(bridge.path("actions").get(0).path("runnable").asBoolean()).isTrue();
			assertThat(bridge.path("actions").get(1).path("runnable").asBoolean())
				.as("an action declaring a second input has no field to carry it")
				.isFalse();
			assertThat(bridge.path("actions").get(1).path("inputTypeNames").get(1).asText()).isEqualTo("TopicBrief");
			assertThat(sidecar.requestLines()).containsExactly("GET /api/v1/actions");
		}
	}

	@Test
	void reportsABridgeThatIsNotRunningInsideASuccessfulResponse() throws Exception {
		String dead = StubSidecar.closedBaseUrl();

		JsonNode bridge = bridges(mvc("gone@" + dead)).path("bridges").get(0);

		assertThat(bridge.path("reachable").asBoolean()).isFalse();
		assertThat(bridge.path("urlProbed").asText()).isEqualTo(dead + "/api/v1/actions");
		assertThat(bridge.path("reason").asText()).isNotBlank();
		assertThat(bridge.path("actions")).isEmpty();
	}

	@Test
	void reportsAConfiguredEntryItCannotParseRatherThanGuessingOne() throws Exception {
		JsonNode body = bridges(mvc("http://localhost:18090"));

		assertThat(body.path("bridges")).as("no bridge is invented for an entry with no name").isEmpty();
		assertThat(body.path("malformedBridges").get(0).asText()).isEqualTo("http://localhost:18090");
	}

	@Test
	void anInstallationWithNoBridgesSaysSoRatherThanFailing() throws Exception {
		JsonNode body = bridges(mvc(""));

		assertThat(body.path("bridges")).isEmpty();
		assertThat(body.path("malformedBridges")).isEmpty();
	}

	@Test
	void reportsTheConfiguredMcpEndpointWithoutEverMakingItABridge() throws Exception {
		try (StubSidecar sidecar = StubSidecar.start()) {
			sidecar.respondToCatalog(200, "[]");

			JsonNode body = bridges(mvc("digest@" + sidecar.baseUrl(), "http://127.0.0.1:8081/mcp"));

			assertThat(body.path("mcpEndpoint").asText()).isEqualTo("http://127.0.0.1:8081/mcp");
			// The MCP endpoint is reported, not probed: it must not appear among the
			// bridges, and
			// reading the surface must not contact it.
			assertThat(body.path("bridges")).hasSize(1);
			assertThat(body.path("bridges").get(0).path("name").asText()).isEqualTo("digest");
			assertThat(sidecar.requestLines()).containsExactly("GET /api/v1/actions");
		}
	}

	@Test
	void reportsNoMcpEndpointWhenNoneIsConfigured() throws Exception {
		JsonNode body = bridges(mvc(""));

		assertThat(body.path("mcpEndpoint").isNull()).isTrue();
	}

	@Test
	void runsAnActionAndReturnsTheContentTheSidecarProduced() throws Exception {
		try (StubSidecar sidecar = StubSidecar.start()) {
			sidecar.respondToExecute(200,
					"{\"content\":\"Digested on the stub: 3 words, 15 chars\",\"timestamp\":\"2026-11-19T00:00:00Z\"}");

			JsonNode body = execute(mvc("digest@" + sidecar.baseUrl()),
					new RemoteExecuteRequest("digest", "remoteDigest", "the input text"));

			assertThat(body.path("error").isNull()).isTrue();
			assertThat(body.path("bridge").asText()).isEqualTo("digest");
			assertThat(body.path("action").asText()).isEqualTo("remoteDigest");
			assertThat(body.path("output").asText()).isEqualTo("Digested on the stub: 3 words, 15 chars");
			assertThat(body.path("reason").isNull()).isTrue();
			assertThat(sidecar.lastExecuteRequestBody()).contains("\"action_name\":\"remoteDigest\"")
				.contains("the input text");
		}
	}

	@Test
	void passesThroughASidecarDeclineWithItsStatusAndItsOwnBody() throws Exception {
		try (StubSidecar sidecar = StubSidecar.start()) {
			sidecar.respondToExecute(400, "{\"error\":\"unknown action: nope\"}");

			JsonNode body = execute(mvc("digest@" + sidecar.baseUrl()),
					new RemoteExecuteRequest("digest", "nope", "text"));

			assertThat(body.path("output").isNull()).as("no result is invented for an action that did not run")
				.isTrue();
			assertThat(body.path("status").asInt()).isEqualTo(400);
			assertThat(body.path("sidecarBody").asText()).isEqualTo("{\"error\":\"unknown action: nope\"}");
		}
	}

	@Test
	void anActionAgainstABridgeThatIsNotRunningIsAStateRatherThanAServerError() throws Exception {
		JsonNode body = execute(mvc("gone@" + StubSidecar.closedBaseUrl()),
				new RemoteExecuteRequest("gone", "remoteDigest", "text"));

		assertThat(body.path("error").isNull()).isTrue();
		assertThat(body.path("output").isNull()).isTrue();
		assertThat(body.path("urlProbed").asText()).endsWith("/api/v1/actions/execute");
		assertThat(body.path("reason").asText()).isNotBlank();
	}

	@Test
	void refusesABridgeConfigurationDoesNotDefineWithoutContactingAnything() throws Exception {
		try (StubSidecar sidecar = StubSidecar.start()) {
			MockMvc mvc = mvc("digest@" + sidecar.baseUrl());

			// First prove the counter counts: the configured bridge does reach the stub.
			// A test that
			// only ever sent a refused request would pass against a stub that received
			// nothing at all.
			execute(mvc, new RemoteExecuteRequest("digest", "remoteDigest", "text"));
			int afterConfiguredBridge = sidecar.requestCount();
			assertThat(afterConfiguredBridge).as("the allowed request reached the stub").isEqualTo(1);

			JsonNode body = execute(mvc, new RemoteExecuteRequest("invent-me", "remoteDigest", "text"));

			assertThat(sidecar.requestCount()).as("an unknown bridge name sends nothing anywhere")
				.isEqualTo(afterConfiguredBridge);
			assertThat(body.path("error").asText()).contains("invent-me").contains("digest");
			assertThat(body.path("urlProbed").isNull()).as("no address was contacted, so there is no URL to report")
				.isTrue();
		}
	}

	@Test
	void refusesAnActionThatNamesNothingBeforeContactingAnything() throws Exception {
		try (StubSidecar sidecar = StubSidecar.start()) {
			JsonNode body = execute(mvc("digest@" + sidecar.baseUrl()),
					new RemoteExecuteRequest("digest", "  ", "text"));

			assertThat(body.path("error").asText()).isNotBlank();
			assertThat(sidecar.requestCount()).isZero();
		}
	}

}
