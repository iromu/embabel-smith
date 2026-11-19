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

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The behaviour this endpoint has that the specification does not describe, measured
 * through the library's facade rather than asserted from its source. Each test is the
 * evidence behind one line of the deviation list in this module's README; the ones that
 * are deliberate omissions are in the change's non-goals.
 *
 * <p>
 * The shape of all of them: the facade in front of this module's handler answers
 * {@code UnsupportedOperationException} from its own dispatch with an HTTP 500 and a
 * JSON-RPC error body, so a client gets a 500 where section 9.5 of the specification
 * defines a structured error code. That is upstream behaviour this module chose not to
 * route around.
 *
 * <p>
 * The source staged here exists and is readable, because one of the deviations below is
 * read off the served card and a card request now fails when its source cannot be read.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "spring.main.web-application-type=SERVLET", "OPENAI_CUSTOM_BASE_URL=http://127.0.0.1:9/v1",
				"OPENAI_CUSTOM_API_KEY=sk-a2a-test", "OPENAI_CUSTOM_MODELS=a2a-test-model",
				"embabel.models.default-llm=a2a-test-model", })
class A2aDeviationRecordTest {

	private static final Path LIBRARY = A2aTestPacks.library(createTempDir("smith-a2a-deviation-lib-"),
			A2aTestPacks.EXTRACT);

	@LocalServerPort
	private int port;

	@DynamicPropertySource
	static void endpoint(DynamicPropertyRegistry registry) {
		registry.add("smith.a2a.source", () -> "folder:" + LIBRARY);
	}

	/**
	 * Section 9.5 defines error codes for a method the server cannot execute. This
	 * endpoint answers 500 instead — for any method the facade does not route, not only
	 * for {@code tasks/list}.
	 */
	@Test
	void aMethodTheFacadeDoesNotRouteAnswersHttp500RatherThanASection9Point5ErrorCode() {
		HttpResponse<String> response = new A2aClient(this.port)
			.rpc("{\"jsonrpc\":\"2.0\",\"id\":1," + "\"method\":\"not/a/method\",\"params\":{}}");

		assertThat(response.statusCode()).isEqualTo(500);
		JsonNode error = A2aClient.json(response.body()).path("error");
		assertThat(error.path("message").asText()).contains("not/a/method is not supported");
		assertThat(error.path("code").asInt()).as("500, not a section 9.5 code").isEqualTo(500);
	}

	/**
	 * The extended card (§3.1.11, §9.4.6) is a non-goal: the card says
	 * {@code supportsAuthenticatedExtendedCard: false}, and the method behind it is not
	 * routed.
	 */
	@Test
	void theExtendedAgentCardMethodIsNotServed() {
		HttpResponse<String> response = new A2aClient(this.port)
			.rpc("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"agent/getAuthenticatedExtendedCard\",\"params\":{}}");

		assertThat(response.statusCode()).isEqualTo(500);
		assertThat(response.body()).contains("agent/getAuthenticatedExtendedCard is not supported");
	}

	/**
	 * Subscribe to task (§3.1.6, {@code tasks/resubscribe}) is a non-goal, and the run
	 * has nothing to resubscribe to: it is synchronous and its record is written when it
	 * finishes.
	 */
	@Test
	void taskResubscriptionIsNotServed() {
		HttpResponse<String> response = new A2aClient(this.port)
			.rpc("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tasks/resubscribe\",\"params\":{\"id\":\"no-run\"}}");

		assertThat(response.statusCode()).isEqualTo(500);
		assertThat(response.body()).contains("tasks/resubscribe is not supported");
	}

	/**
	 * Card signing (§8.4) is a non-goal: the served card declares no security schemes and
	 * no requirements, which is what the absence of signing looks like on the wire.
	 */
	@Test
	void theCardDeclaresNoSecuritySchemes() {
		HttpResponse<String> response = new A2aClient(this.port).get("/a2a/.well-known/agent.json");

		JsonNode card = A2aClient.json(response.body());
		// The positive control on the two assertions below: this is the card with its
		// skills on it, so an empty securitySchemes means the card omits them rather than
		// the request never landed.
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(card.path("protocolVersion").asText()).isEqualTo("0.3.0");
		assertThat(card.path("skills")).isNotEmpty();
		assertThat(card.path("securitySchemes")).isEmpty();
		assertThat(card.path("security")).isEmpty();
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
