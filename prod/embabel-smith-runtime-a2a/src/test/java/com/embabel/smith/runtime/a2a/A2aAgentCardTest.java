/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.a2a;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.embabel.agent.core.AgentPlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The agent card over real HTTP, from a real deployed source. Both packs staged here are
 * allowed by the configured tier, so the card advertises one skill per exported goal, and
 * the document a client reads is checked field by field against what section 1 measured
 * about the library.
 *
 * <p>
 * The library gets onto the card by being ingested into the platform once for this
 * context — ingestion is the only thing that puts goals on the platform, and the endpoint
 * itself ingests only when a run asks it to. Nothing here runs a goal, so no model
 * endpoint is involved and the tests are offline.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "spring.main.web-application-type=SERVLET", "OPENAI_CUSTOM_BASE_URL=http://127.0.0.1:9/v1",
				"OPENAI_CUSTOM_API_KEY=sk-a2a-test", "OPENAI_CUSTOM_MODELS=a2a-test-model",
				"embabel.models.default-llm=a2a-test-model", "smith.a2a.trust-tier=STANDARD", })
class A2aAgentCardTest {

	private static final Path TRACE_DIR = createTempDir("smith-a2a-card-trace-");

	private static final Path LIBRARY = A2aTestPacks.library(createTempDir("smith-a2a-card-lib-"), A2aTestPacks.EXTRACT,
			A2aTestPacks.EXPLAIN);

	private static final AtomicBoolean DEPLOYED = new AtomicBoolean();

	@LocalServerPort
	private int port;

	@Autowired
	private AgentPlatform platform;

	@DynamicPropertySource
	static void endpoint(DynamicPropertyRegistry registry) {
		registry.add("smith.a2a.trace-dir", TRACE_DIR::toString);
		registry.add("smith.a2a.source", () -> "folder:" + LIBRARY);
	}

	@BeforeEach
	void deployTheSourceOnce() {
		if (DEPLOYED.compareAndSet(false, true)) {
			A2aTestPacks.deploy(this.platform, LIBRARY);
		}
	}

	@Test
	void theCardCarriesOneSkillPerGoalOfTheDeployedSource() {
		JsonNode card = card();

		assertThat(card.path("skills")).hasSize(2);
		List<String> advertised = new ArrayList<>();
		card.path("skills").forEach((skill) -> advertised.add(skill.path("name").asText()));
		assertThat(advertised).containsExactlyInAnyOrder("briefDone", "explainDone");
	}

	@Test
	void theCardUrlNamesThePathTheEndpointActuallyAcceptsPostsOn() {
		String url = card().path("url").asText();

		assertThat(url).isEqualTo("http://127.0.0.1:" + this.port + "/a2a");
		// Not just a string: posting to the url the card hands a client is answered.
		HttpResponse<String> response = new A2aClient(this.port).rpc("""
				{"jsonrpc":"2.0","id":1,"method":"tasks/get","params":{"id":"no-such-run","historyLength":0}}""");
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("-32001");
	}

	/**
	 * The claims a client reads before it decides how to talk to this endpoint. Each
	 * value is the one section 1 recorded from {@code embabel-agent-a2a:1.5.4}; none of
	 * them is set by this module.
	 */
	@Test
	void theCardDocumentCarriesTheProtocolVersionAndDefaultsTheLibraryIsKnownFor() {
		JsonNode card = card();

		assertThat(card.path("name").asText()).isEqualTo(this.platform.getName());
		assertThat(card.path("version").asText()).isEqualTo("0.1.0-SNAPSHOT");
		assertThat(card.path("documentationUrl").asText()).isEqualTo("https://embabel.com/docs");
		assertThat(card.path("provider").path("organization").asText()).isEqualTo("Embabel");
		assertThat(card.path("capabilities").path("streaming").asBoolean()).isTrue();
		assertThat(card.path("capabilities").path("pushNotifications").asBoolean()).isFalse();
		assertThat(card.path("capabilities").path("stateTransitionHistory").asBoolean()).isFalse();
		assertThat(card.path("capabilities").path("extensions")).isEmpty();
		assertThat(A2aClient.textList(card.path("defaultInputModes"))).containsExactly("application/json",
				"text/plain");
		assertThat(A2aClient.textList(card.path("defaultOutputModes"))).containsExactly("application/json",
				"text/plain");
		assertThat(card.path("supportsAuthenticatedExtendedCard").asBoolean()).isFalse();
		// The claim that tells a client which protocol this endpoint speaks. Section 1
		// recorded 0.3.0 from the card the library serves; the current specification is
		// 1.0.
		assertThat(card.path("protocolVersion").asText()).isEqualTo("0.3.0");
	}

	/**
	 * Section 8.2 of the current (1.0) specification puts discovery at
	 * {@code /.well-known/agent-card.json}. This endpoint does not serve it: the library
	 * registers {@code GET /{path}/.well-known/agent.json}, the 0.3 location, and the
	 * card's own {@code protocolVersion} is what tells a 1.0 client it is looking in the
	 * wrong place. The two paths differ by one filename, which is why this is asserted
	 * rather than assumed.
	 */
	@Test
	void discoveryIsServedAtTheLibrarysWellKnownAgentJsonPathNotTheSpecificationSection8Point2AgentCardJsonPath() {
		A2aClient client = new A2aClient(this.port);

		assertThat(client.get("/a2a/.well-known/agent.json").statusCode()).isEqualTo(200);
		assertThat(client.get("/a2a/.well-known/agent-card.json").statusCode())
			.as("section 8.2's filename under this endpoint's path")
			.isEqualTo(404);
		assertThat(client.get("/.well-known/agent-card.json").statusCode()).as("section 8.2's path at the server root")
			.isEqualTo(404);
	}

	private JsonNode card() {
		HttpResponse<String> response = new A2aClient(this.port)
			.get("/" + A2aConfiguration.ENDPOINT_PATH + "/.well-known/agent.json");
		assertThat(response.statusCode()).isEqualTo(200);
		return A2aClient.json(response.body());
	}

	private static Path createTempDir(String prefix) {
		try {
			return Files.createTempDirectory(prefix);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
