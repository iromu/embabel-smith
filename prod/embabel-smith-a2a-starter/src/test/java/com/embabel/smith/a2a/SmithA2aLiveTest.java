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
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two happy paths the offline tests cannot reach: {@code SendMessage} (§9.4.1) and
 * {@code SendStreamingMessage} (§9.4.2) run a real goal over a real model endpoint and
 * come back with the goal's output as the task's artifact.
 *
 * <p>
 * These are the endpoint-driven tests of this module. They are tagged {@code live}, which
 * the root pom excludes from {@code make test}, and gated on
 * {@code OPENAI_CUSTOM_BASE_URL}, so they stay off unless an endpoint is actually
 * configured. {@code make test-live} is the entry point that selects them.
 *
 * <p>
 * The source is the single-pack {@code extract} library, whose {@code briefDone} goal is
 * the one the runtime's own live test proves this endpoint can achieve.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "smith.a2a.enabled=true", "spring.main.web-application-type=SERVLET", })
@Tag("live")
@EnabledIfEnvironmentVariable(named = "OPENAI_CUSTOM_BASE_URL", matches = ".+")
class SmithA2aLiveTest {

	private static final Path TRACE_DIR = createTempDir("smith-a2a-live-trace-");

	private static final Path LIBRARY = A2aTestPacks.library(createTempDir("smith-a2a-live-lib-"),
			A2aTestPacks.EXTRACT);

	@LocalServerPort
	private int port;

	@DynamicPropertySource
	static void endpoint(DynamicPropertyRegistry registry) {
		registry.add("smith.a2a.trace-dir", TRACE_DIR::toString);
		registry.add("smith.a2a.source", () -> "folder:" + LIBRARY);
	}

	@Test
	void sendMessageRunsTheGoalAndAnswersACompletedTaskCarryingTheOutputAsAnArtifact() {
		HttpResponse<String> response = new A2aClient(this.port).rpc("""
				{"jsonrpc":"2.0","id":1,"method":"message/send","params":{"message":{"role":"user","kind":"message",\
				"messageId":"live-1","parts":[{"kind":"text","text":"Explain how a coffee machine works"}]},\
				"metadata":{"goal":"briefDone"}}}""");

		assertThat(response.statusCode()).isEqualTo(200);
		JsonNode task = A2aClient.json(response.body()).path("result");
		assertThat(task.path("kind").asText()).isEqualTo("task");
		assertThat(task.path("status").path("state").asText())
			.as("the run failed with: %s", task.path("status").path("message"))
			.isEqualTo("completed");
		assertThat(task.path("artifacts").get(0).path("parts").get(0).path("text").asText()).isNotBlank();
	}

	@Test
	void sendStreamingMessageRunsTheGoalAndClosesWithATerminalCompletedStatusUpdate() {
		HttpResponse<String> response = new A2aClient(this.port).streaming("""
				{"jsonrpc":"2.0","id":2,"method":"message/stream","params":{"message":{"role":"user","kind":"message",\
				"messageId":"live-2","parts":[{"kind":"text","text":"Explain how a coffee machine works"}]},\
				"metadata":{"goal":"briefDone"}}}""");

		List<String> events = A2aClient.events(response.body());
		assertThat(events).isNotEmpty();
		JsonNode first = A2aClient.json(events.get(0)).path("result");
		assertThat(first.path("kind").asText()).isEqualTo("task");
		assertThat(first.path("status").path("state").asText())
			.as("the run failed with: %s", first.path("status").path("message"))
			.isEqualTo("completed");
		JsonNode last = A2aClient.json(events.get(events.size() - 1)).path("result");
		assertThat(last.path("kind").asText()).isEqualTo("status-update");
		assertThat(last.path("final").asBoolean()).isTrue();
		assertThat(last.path("status").path("state").asText()).isEqualTo("completed");
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
