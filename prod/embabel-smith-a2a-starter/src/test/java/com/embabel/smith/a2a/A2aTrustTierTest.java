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

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The configured trust tier decides both what the card advertises and what a run may do,
 * for a source that contains a pack the standard tier disallows: {@code shell}, whose
 * step declares the {@code shell} tool group.
 *
 * <p>
 * The card is read from the configured source, so nothing here ingests anything first —
 * and the card's size is a claim, because the source is a fixed list rather than whatever
 * the platform happens to have accumulated.
 *
 * <p>
 * The blocked run is a real run of the real service against the real platform — it is
 * refused by the runtime's tier gate before any step executes, so no model call happens
 * and the test stays offline.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "smith.a2a.enabled=true", "spring.main.web-application-type=SERVLET",
				"OPENAI_CUSTOM_BASE_URL=http://127.0.0.1:9/v1", "OPENAI_CUSTOM_API_KEY=sk-a2a-test",
				"OPENAI_CUSTOM_MODELS=a2a-test-model", "embabel.models.default-llm=a2a-test-model",
				"smith.a2a.trust-tier=STANDARD", })
class A2aTrustTierTest {

	private static final Path TRACE_DIR = createTempDir("smith-a2a-tier-trace-");

	private static final Path LIBRARY = A2aTestPacks.library(createTempDir("smith-a2a-tier-lib-"), A2aTestPacks.EXPLAIN,
			A2aTestPacks.SHELL);

	@LocalServerPort
	private int port;

	@DynamicPropertySource
	static void endpoint(DynamicPropertyRegistry registry) {
		registry.add("smith.a2a.trace-dir", TRACE_DIR::toString);
		registry.add("smith.a2a.source", () -> "folder:" + LIBRARY);
	}

	@Test
	void theCardAdvertisesTheAllowedGoalAndOmitsTheGoalOfTheDisallowedPack() {
		List<String> advertised = advertisedGoals();

		assertThat(advertised).as("the shell pack declares a group this tier cannot run")
			.containsExactly("explainDone");
	}

	@Test
	void attemptingTheDisallowedGoalAnywayIsAFailedTaskNamingTheTierAndTheGroup() {
		HttpResponse<String> response = new A2aClient(this.port).rpc("""
				{"jsonrpc":"2.0","id":1,"method":"message/send","params":{"message":{"role":"user","kind":"message",\
				"messageId":"m-1","parts":[{"kind":"text","text":"ls"}]},"metadata":{"goal":"cliDone"}}}""");

		assertThat(response.statusCode()).isEqualTo(200);
		JsonNode task = A2aClient.json(response.body()).path("result");
		assertThat(task.path("status").path("state").asText()).isEqualTo("failed");
		assertThat(task.path("status").path("message").path("parts").get(0).path("text").asText())
			.contains("Run blocked by trust tier")
			.contains("standard")
			.contains("shell");
	}

	private List<String> advertisedGoals() {
		HttpResponse<String> response = new A2aClient(this.port).get("/a2a/.well-known/agent.json");
		assertThat(response.statusCode()).isEqualTo(200);
		List<String> names = new ArrayList<>();
		A2aClient.json(response.body()).path("skills").forEach((skill) -> names.add(skill.path("name").asText()));
		return names;
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
