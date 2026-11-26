/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.a2a;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import com.embabel.smith.runtime.AgentSpecRunService;
import com.embabel.smith.runtime.AgentSpecRunService.RunResult;
import com.embabel.smith.common.trace.TraceRecord;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@code message/stream} over real HTTP: the events a run actually produced, in order,
 * then the stream closes.
 *
 * <p>
 * The run service is stubbed here because no offline source can produce a
 * <em>successful</em> run, and only a successful run has an artifact to stream.
 * Everything above the stub is the real thing: the library's registrar, its facade, its
 * SSE serialization, and this module's event sequence.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "spring.main.web-application-type=SERVLET", "smith.a2a.enabled=true",
				"OPENAI_CUSTOM_BASE_URL=http://127.0.0.1:9/v1", "OPENAI_CUSTOM_API_KEY=sk-a2a-test",
				"OPENAI_CUSTOM_MODELS=a2a-test-model", "embabel.models.default-llm=a2a-test-model",
				"smith.a2a.source=folder:/tmp/a2a-library", })
class A2aStreamingOverHttpTest {

	private static final Path TRACE_DIR = createTraceDir();

	private static final String OUTPUT = "The report says revenue rose.";

	@LocalServerPort
	private int port;

	@MockitoBean
	private AgentSpecRunService runService;

	@DynamicPropertySource
	static void traceDir(DynamicPropertyRegistry registry) {
		registry.add("smith.a2a.trace-dir", TRACE_DIR::toString);
	}

	@Test
	void theStreamCarriesTheTaskThenItsArtifactThenTheFinalStatusAndCloses() {
		when(this.runService.run(any(), any(), any(), any(), any()))
			.thenReturn(new RunResult(true, OUTPUT, null, trace("run-stream-1")));

		HttpResponse<String> response = stream("""
				{"jsonrpc":"2.0","id":1,"method":"message/stream","params":{"message":{"role":"user","kind":"message",\
				"messageId":"m-1","parts":[{"kind":"text","text":"summarize it"}]}}}""");

		List<String> events = events(response.body());
		// Exactly the events the run produced: no invented WORKING progress, because the
		// run reports none while it is running.
		assertThat(events).hasSize(3);
		assertThat(events.get(0)).contains("\"kind\":\"task\"").contains("\"state\":\"completed\"");
		assertThat(events.get(1)).contains("\"kind\":\"artifact-update\"").contains(OUTPUT);
		assertThat(events.get(2)).contains("\"kind\":\"status-update\"")
			.contains("\"state\":\"completed\"")
			.contains("\"final\":true");
	}

	@Test
	void aFailedRunStreamsAFailedStatusWithoutAnArtifactEvent() {
		when(this.runService.run(any(), any(), any(), any(), any()))
			.thenReturn(new RunResult(false, null, "Goal 'ghostDone' not achieved", trace("run-stream-2")));

		HttpResponse<String> response = stream("""
				{"jsonrpc":"2.0","id":2,"method":"message/stream","params":{"message":{"role":"user","kind":"message",\
				"messageId":"m-2","parts":[{"kind":"text","text":"go"}]}}}""");

		List<String> events = events(response.body());
		assertThat(events).hasSize(2);
		assertThat(events.get(0)).contains("\"kind\":\"task\"")
			.contains("\"state\":\"failed\"")
			.contains("Goal 'ghostDone' not achieved");
		assertThat(events.get(1)).contains("\"kind\":\"status-update\"").contains("\"final\":true");
	}

	private HttpResponse<String> stream(String body) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + this.port + "/a2a"))
				.header("Content-Type", "application/json")
				.header("Accept", "text/event-stream")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();
			return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static List<String> events(String body) {
		return body.lines().filter((line) -> line.startsWith("data:")).map((line) -> line.substring(5)).toList();
	}

	private static TraceRecord trace(String runId) {
		return new TraceRecord(runId, Instant.parse("2026-11-19T10:00:00Z"),
				new TraceRecord.SourceRef("folder:/tmp/a2a-library", null, null),
				new TraceRecord.PackRef("explain", "sha256:abc"), "explainDone", "summarize it", "GOAP", "standard",
				List.of(), true, null, OUTPUT);
	}

	private static Path createTraceDir() {
		try {
			return Files.createTempDirectory("smith-a2a-stream-test-");
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
