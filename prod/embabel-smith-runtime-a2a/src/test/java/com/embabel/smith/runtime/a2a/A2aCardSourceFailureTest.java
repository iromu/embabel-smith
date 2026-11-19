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
 * The card request when the configured source cannot be read. The endpoint deliberately
 * starts with a source directory that does not exist yet, then stages a pack into it, so
 * one test can observe both halves of the rule: an unreadable source raises rather than
 * serving an empty skill list, and the failure is not remembered, so the next request
 * succeeds once the source is readable.
 *
 * <p>
 * Boot is not affected by the unreadable source — the read is lazy — which is what lets
 * the context come up at all here.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "spring.main.web-application-type=SERVLET", "OPENAI_CUSTOM_BASE_URL=http://127.0.0.1:9/v1",
				"OPENAI_CUSTOM_API_KEY=sk-a2a-test", "OPENAI_CUSTOM_MODELS=a2a-test-model",
				"embabel.models.default-llm=a2a-test-model", "smith.a2a.trust-tier=STANDARD", })
class A2aCardSourceFailureTest {

	private static final Path TRACE_DIR = createTempDir("smith-a2a-card-failure-trace-");

	private static final Path LIBRARY = createTempDir("smith-a2a-card-failure-lib-").resolve("packs");

	@LocalServerPort
	private int port;

	@DynamicPropertySource
	static void endpoint(DynamicPropertyRegistry registry) {
		registry.add("smith.a2a.trace-dir", TRACE_DIR::toString);
		registry.add("smith.a2a.source", () -> "folder:" + LIBRARY);
	}

	@Test
	void anUnreadableSourceFailsTheCardRequestAndTheNextOneSucceedsOnceTheSourceIsReadable() {
		assertThat(card().statusCode())
			.as("an empty card would tell a client the agent does nothing, so this raises instead")
			.isEqualTo(500);

		A2aTestPacks.library(LIBRARY, A2aTestPacks.EXTRACT);

		HttpResponse<String> recovered = card();
		assertThat(recovered.statusCode()).as("the failure was not cached").isEqualTo(200);
		JsonNode card = A2aClient.json(recovered.body());
		assertThat(card.path("skills")).singleElement()
			.extracting((skill) -> skill.path("name").asText())
			.isEqualTo("briefDone");
	}

	private HttpResponse<String> card() {
		return new A2aClient(this.port).get("/" + A2aConfiguration.ENDPOINT_PATH + "/.well-known/agent.json");
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
