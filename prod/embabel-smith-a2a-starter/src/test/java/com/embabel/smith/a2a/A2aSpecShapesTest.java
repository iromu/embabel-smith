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
import java.time.Instant;
import java.util.List;

import com.embabel.smith.runtime.AgentSpecRunService;
import com.embabel.smith.runtime.AgentSpecRunService.RunResult;
import com.embabel.smith.common.trace.TraceRecord;
import com.embabel.smith.common.trace.TraceStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * What the wire actually carries for each served operation, checked against the shapes
 * the A2A specification marks required — Task (§4.1.1: {@code id}, {@code status}),
 * Message (§4.1.4: {@code messageId}, {@code role}, {@code parts}), Artifact (§4.1.7:
 * {@code artifactId}, {@code parts}) and Part (§4.1.6: a discriminated union, so each
 * part names its {@code kind}).
 *
 * <p>
 * Each test names the section it checks. The run service is stubbed so a run can succeed
 * without an endpoint; everything above the stub — the facade, the dispatch, the JSON
 * serialization — is the real one.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "smith.a2a.enabled=true", "spring.main.web-application-type=SERVLET",
				"OPENAI_CUSTOM_BASE_URL=http://127.0.0.1:9/v1", "OPENAI_CUSTOM_API_KEY=sk-a2a-test",
				"OPENAI_CUSTOM_MODELS=a2a-test-model", "embabel.models.default-llm=a2a-test-model",
				"smith.a2a.source=folder:/tmp/a2a-library", })
class A2aSpecShapesTest {

	private static final Path TRACE_DIR = createTempDir("smith-a2a-shapes-");

	private static final String OUTPUT = "The report says revenue rose.";

	@LocalServerPort
	private int port;

	@Autowired
	private TraceStore traceStore;

	@MockitoBean
	private AgentSpecRunService runService;

	@DynamicPropertySource
	static void traceDir(DynamicPropertyRegistry registry) {
		registry.add("smith.a2a.trace-dir", TRACE_DIR::toString);
	}

	/**
	 * Section 9.4.1 binds {@code SendMessage}; section 4.1.1 requires a Task to carry
	 * {@code id} and {@code status}, section 4.1.7 requires an Artifact to carry
	 * {@code artifactId} and {@code parts}, and section 4.1.6 requires each Part to say
	 * which of text, file or data it is.
	 */
	@Test
	void sendMessageAnswersTheTaskAndArtifactShapesOfSections4Point1Point1And4Point1Point7() {
		when(this.runService.run(any(), any(), any(), any(), any()))
			.thenReturn(new RunResult(true, OUTPUT, null, record("run-send-1", true, null, OUTPUT)));

		JsonNode result = rpcResult("""
				{"jsonrpc":"2.0","id":11,"method":"message/send","params":{"message":{"role":"user","kind":"message",\
				"messageId":"m-1","parts":[{"kind":"text","text":"summarize it"}]}}}""");

		assertThat(result.path("kind").asText()).isEqualTo("task");
		assertThat(result.path("id").asText()).isEqualTo("run-send-1");
		assertThat(result.path("status").path("state").asText()).isEqualTo("completed");
		JsonNode artifact = result.path("artifacts").get(0);
		assertThat(artifact.path("artifactId").asText()).isNotBlank();
		assertThat(artifact.path("parts")).hasSize(1);
		assertThat(artifact.path("parts").get(0).path("kind").asText()).isEqualTo("text");
		assertThat(artifact.path("parts").get(0).path("text").asText()).isEqualTo(OUTPUT);
	}

	/**
	 * Section 4.1.4 requires a Message to carry {@code messageId}, {@code role} and
	 * {@code parts}. A failed task is where this endpoint emits one: the refusal reason
	 * travels as the status message.
	 */
	@Test
	void aFailedTaskCarriesTheMessageShapeOfSection4Point1Point4() {
		when(this.runService.run(any(), any(), any(), any(), any())).thenReturn(new RunResult(false, null,
				"Goal 'ghostDone' not achieved", record("run-fail-1", false, "Goal 'ghostDone' not achieved", null)));

		JsonNode message = rpcResult("""
				{"jsonrpc":"2.0","id":12,"method":"message/send","params":{"message":{"role":"user","kind":"message",\
				"messageId":"m-2","parts":[{"kind":"text","text":"go"}]}}}""").path("status").path("message");

		assertThat(message.path("messageId").asText()).isNotBlank();
		assertThat(message.path("role").asText()).isEqualTo("agent");
		assertThat(message.path("parts").get(0).path("text").asText()).contains("Goal 'ghostDone' not achieved");
		assertThat(message.path("kind").asText()).isEqualTo("message");
	}

	/**
	 * Section 9.4.2 binds {@code SendStreamingMessage} to server-sent events. The events
	 * this endpoint emits are the task, its artifact, and the terminal status update —
	 * each carrying the shape its own section defines, and the JSON-RPC envelope (§9.4)
	 * keeping the request's id on every one.
	 */
	@Test
	void sendStreamingMessageAnswersTheEventShapesOfSection9Point4Point2() {
		when(this.runService.run(any(), any(), any(), any(), any()))
			.thenReturn(new RunResult(true, OUTPUT, null, record("run-stream-1", true, null, OUTPUT)));

		HttpResponse<String> response = new A2aClient(this.port).streaming("""
				{"jsonrpc":"2.0","id":13,"method":"message/stream","params":{"message":{"role":"user",\
				"kind":"message","messageId":"m-3","parts":[{"kind":"text","text":"summarize it"}]}}}""");

		List<String> events = A2aClient.events(response.body());
		assertThat(events).hasSize(3);
		assertThat(events).allSatisfy((event) -> {
			JsonNode envelope = A2aClient.json(event);
			assertThat(envelope.path("jsonrpc").asText()).isEqualTo("2.0");
			assertThat(envelope.path("id").asInt()).isEqualTo(13);
		});

		JsonNode task = A2aClient.json(events.get(0)).path("result");
		assertThat(task.path("kind").asText()).isEqualTo("task");
		assertThat(task.path("id").asText()).isEqualTo("run-stream-1");
		assertThat(task.path("status").path("state").asText()).isEqualTo("completed");

		JsonNode artifactUpdate = A2aClient.json(events.get(1)).path("result");
		assertThat(artifactUpdate.path("kind").asText()).isEqualTo("artifact-update");
		assertThat(artifactUpdate.path("artifact").path("artifactId").asText()).isNotBlank();
		assertThat(artifactUpdate.path("artifact").path("parts").get(0).path("text").asText()).isEqualTo(OUTPUT);

		JsonNode statusUpdate = A2aClient.json(events.get(2)).path("result");
		assertThat(statusUpdate.path("kind").asText()).isEqualTo("status-update");
		assertThat(statusUpdate.path("status").path("state").asText()).isEqualTo("completed");
		assertThat(statusUpdate.path("final").asBoolean()).isTrue();
	}

	/**
	 * Section 9.4.3 binds {@code GetTask}. The task read back from the trace store is the
	 * same shape as the one a send returned.
	 */
	@Test
	void getTaskAnswersTheTaskShapeOfSection9Point4Point3() {
		this.traceStore.append(record("run-get-1", true, null, "the recorded excerpt"));

		JsonNode envelope = rpc("""
				{"jsonrpc":"2.0","id":14,"method":"tasks/get","params":{"id":"run-get-1","historyLength":0}}""");

		assertThat(envelope.path("jsonrpc").asText()).isEqualTo("2.0");
		assertThat(envelope.path("id").asInt()).isEqualTo(14);
		JsonNode task = envelope.path("result");
		assertThat(task.path("kind").asText()).isEqualTo("task");
		assertThat(task.path("id").asText()).isEqualTo("run-get-1");
		assertThat(task.path("status").path("state").asText()).isEqualTo("completed");
		assertThat(task.path("artifacts").get(0).path("artifactId").asText()).isNotBlank();
	}

	/**
	 * Section 9.4.5 binds {@code CancelTask}. This endpoint answers with the task in the
	 * state it really is in rather than pretending to stop a finished run, and it still
	 * answers with a Task of the required shape.
	 */
	@Test
	void cancelTaskAnswersTheTaskShapeOfSection9Point4Point5() {
		this.traceStore.append(record("run-cancel-1", true, null, "done"));

		JsonNode task = rpc("""
				{"jsonrpc":"2.0","id":15,"method":"tasks/cancel","params":{"id":"run-cancel-1"}}""").path("result");

		assertThat(task.path("kind").asText()).isEqualTo("task");
		assertThat(task.path("id").asText()).isEqualTo("run-cancel-1");
		assertThat(task.path("status").path("state").asText()).isEqualTo("completed");
		assertThat(task.path("status").path("message").path("parts").get(0).path("text").asText())
			.contains("was not cancelled");
	}

	private JsonNode rpcResult(String body) {
		JsonNode envelope = rpc(body);
		assertThat(envelope.path("jsonrpc").asText()).isEqualTo("2.0");
		assertThat(envelope.has("id")).as("the envelope echoes the request id, section 9.4").isTrue();
		assertThat(envelope.has("result")).as("no error: " + envelope).isTrue();
		return envelope.path("result");
	}

	private JsonNode rpc(String body) {
		HttpResponse<String> response = new A2aClient(this.port).rpc(body);
		assertThat(response.statusCode()).isEqualTo(200);
		return A2aClient.json(response.body());
	}

	private static TraceRecord record(String runId, boolean success, String error, String outputExcerpt) {
		return new TraceRecord(runId, Instant.parse("2026-11-19T10:00:00Z"),
				new TraceRecord.SourceRef("folder:/tmp/a2a-library", null, null),
				new TraceRecord.PackRef("explain", "sha256:abc"), "explainDone", "summarize it", "GOAP", "standard",
				List.of(), success, error, outputExcerpt);
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
