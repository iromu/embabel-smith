/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.a2a;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.embabel.agent.a2a.server.AgentCardHandler;
import com.embabel.agent.core.AgentPlatform;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The agent card over real HTTP. Both packs staged here are allowed by the configured
 * tier, so the card advertises one skill per exported goal, and the document a client
 * reads is checked field by field against what section 1 measured about the library.
 *
 * <p>
 * Nothing here ingests or runs a goal before reading the card: the skills come from the
 * configured source, so a freshly started endpoint can already describe itself, and no
 * model endpoint is involved. The one test that does send requests sends them for a goal
 * the source does not export, so the run is refused at goal selection — after the ingest
 * that used to be the card's problem, and before anything could reach a model.
 *
 * <p>
 * The order matters: the first test's claim is that the card is complete with nothing run
 * yet, so it has to observe the context before the last one sends anything.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "smith.a2a.enabled=true", "spring.main.web-application-type=SERVLET",
				"OPENAI_CUSTOM_BASE_URL=http://127.0.0.1:9/v1", "OPENAI_CUSTOM_API_KEY=sk-a2a-test",
				"OPENAI_CUSTOM_MODELS=a2a-test-model", "embabel.models.default-llm=a2a-test-model",
				"smith.a2a.trust-tier=STANDARD", })
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class A2aAgentCardTest {

	private static final Path TRACE_DIR = createTempDir("smith-a2a-card-trace-");

	private static final Path LIBRARY = A2aTestPacks.library(createTempDir("smith-a2a-card-lib-"), A2aTestPacks.EXTRACT,
			A2aTestPacks.EXPLAIN);

	@LocalServerPort
	private int port;

	@Autowired
	private AgentPlatform platform;

	@Autowired
	private ApplicationContext context;

	@DynamicPropertySource
	static void endpoint(DynamicPropertyRegistry registry) {
		registry.add("smith.a2a.trace-dir", TRACE_DIR::toString);
		registry.add("smith.a2a.source", () -> "folder:" + LIBRARY);
	}

	/**
	 * The whole point of suppressing the library's own auto-configuration: exactly one
	 * card handler, so the registrar writes one {@code /a2a} mapping rather than failing
	 * the boot on an ambiguous one. Both handlers on their own are enough to break the
	 * boot, which is why the count is asserted here rather than inferred from the card
	 * answering.
	 */
	@Test
	void theEnabledContextRegistersExactlyOneAgentCardHandler() {
		assertThat(this.context.getBeanNamesForType(AgentCardHandler.class))
			.as("card handlers registered in the enabled context")
			.singleElement()
			.satisfies((name) -> assertThat(name).isEqualTo("sourceDerivedAgentCardHandler"));
	}

	/**
	 * The assertion the previous mechanism could not make: the source is configured,
	 * nothing has been ingested or run, and the card already lists both goals. Derived
	 * from {@code AgentPlatform.getGoals()} instead, this card would be empty here — that
	 * set only gains a goal when something is deployed.
	 */
	@Test
	@Order(1)
	void theCardListsEveryGoalOfTheConfiguredSourceBeforeAnythingHasRun() {
		JsonNode card = card();

		assertThat(card.path("skills")).hasSize(2);
		assertThat(advertisedNames(card)).containsExactlyInAnyOrder("briefDone", "explainDone");
	}

	@Test
	void theCardSkillCarriesTheGoalItAdvertises() {
		JsonNode skill = card().path("skills").get(0);

		// A no-code goal has no tags and no examples — those belong to @Agent on a Java
		// class — so both are present and empty rather than invented.
		assertThat(skill.path("id").asText()).isEqualTo(skill.path("name").asText());
		assertThat(skill.path("description").asText()).isNotBlank();
		assertThat(skill.path("tags")).isEmpty();
		assertThat(skill.path("examples")).isEmpty();
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
	 * value is the one section 1 recorded from {@code embabel-agent-a2a:1.5.4}; only
	 * {@code skills} is assembled by this module, and it is asserted elsewhere.
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

	/**
	 * The second half of the defect this change fixes. Every run ends in
	 * {@code platform.deploy(agent)} under a fresh scope name, so a card built from the
	 * platform's goals grew a duplicate skill per run. Two requests are sent here — for a
	 * goal the source does not export, so each one deploys the source and then fails at
	 * goal selection without reaching a model — and the card still lists each goal once.
	 */
	@Test
	@Order(2)
	void twoRunsOfTheSameSourceLeaveOneSkillPerGoalOnTheCard() {
		for (int run = 1; run <= 2; run++) {
			HttpResponse<String> response = new A2aClient(this.port)
				.rpc("""
						{"jsonrpc":"2.0","id":%d,"method":"message/send","params":{"message":{"role":"user","kind":"message",\
						"messageId":"m-%d","parts":[{"kind":"text","text":"go"}]},"metadata":{"goal":"noSuchGoal"}}}
						"""
					.formatted(run, run));
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(A2aClient.json(response.body()).path("result").path("status").path("state").asText())
				.as("the run deploys the source, then fails at goal selection")
				.isEqualTo("failed");
		}

		JsonNode card = card();

		assertThat(advertisedNames(card)).containsExactlyInAnyOrder("briefDone", "explainDone");
		assertThat(card.path("skills")).as("no duplicate skill per run").hasSize(2);
	}

	private List<String> advertisedNames(JsonNode card) {
		List<String> advertised = new ArrayList<>();
		card.path("skills").forEach((skill) -> advertised.add(skill.path("name").asText()));
		return advertised;
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
