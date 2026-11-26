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

import com.embabel.agent.a2a.server.AgentCardHandler;
import com.embabel.smith.common.trace.TraceRecord;
import com.embabel.smith.common.trace.TraceStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The A2A endpoint over real HTTP: the library's own registrar, JSON-RPC facade and
 * dispatch, driving this repo's handler, with no model endpoint configured.
 *
 * <p>
 * The runs are real and they all fail — the configured source does not exist — which is
 * what keeps these offline while still being end-to-end. A failed run still appends a
 * trace record, so the task id a request gets back can be found in the trace file. The
 * library's own handler writes no trace record, so that lookup is also the proof that
 * this module's handler is the one serving {@code /a2a}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "spring.main.web-application-type=SERVLET", "smith.a2a.enabled=true",
				"OPENAI_CUSTOM_BASE_URL=http://127.0.0.1:9/v1", "OPENAI_CUSTOM_API_KEY=sk-a2a-test",
				"OPENAI_CUSTOM_MODELS=a2a-test-model", "embabel.models.default-llm=a2a-test-model",
				// A source that resolves to nothing: the run fails, records, and answers.
				"smith.a2a.source=folder:/tmp/smith-a2a-absent", "smith.a2a.trust-tier=HIGH", })
class A2aEndpointOverHttpTest {

	private static final Path TRACE_DIR = createTraceDir();

	@LocalServerPort
	private int port;

	@Autowired
	private ApplicationContext context;

	@Autowired
	private TraceStore traceStore;

	@DynamicPropertySource
	static void traceDir(DynamicPropertyRegistry registry) {
		registry.add("smith.a2a.trace-dir", TRACE_DIR::toString);
	}

	@Test
	void theEndpointIsRegisteredOnceUnderThePathTheLibraryRegisters() {
		// A second card handler would register the same mapping and fail the boot with an
		// ambiguous mapping, so the fact that this context came up at all is
		// load-bearing.
		assertThat(this.context.getBeanNamesForType(AgentCardHandler.class)).hasSize(1);
		assertThat(rpc("""
				{"jsonrpc":"2.0","id":1,"method":"tasks/get","params":{"id":"no-such-run","historyLength":0}}""")
			.body()).contains("-32001");
	}

	@Test
	void aSendRequestRunsAndAnswersWithATaskWhoseIdTheTraceStoreKnows() {
		HttpResponse<String> response = rpc("""
				{"jsonrpc":"2.0","id":1,"method":"message/send","params":{"message":{"role":"user","kind":"message",\
				"messageId":"m-1","parts":[{"kind":"text","text":"summarize it"}]}}}""");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("\"kind\":\"task\"").contains("\"state\":\"failed\"");
		String taskId = taskId(response.body());
		assertThat(this.traceStore.read().records()).extracting(TraceRecord::runId).contains(taskId);
		assertThat(this.traceStore.read().records()).filteredOn((record) -> record.runId().equals(taskId))
			.singleElement()
			.satisfies((record) -> {
				// The tier is the endpoint's configured one, so a policy block stays
				// distinguishable from a bad spec.
				assertThat(record.trustTier()).isEqualTo("high");
				assertThat(record.input()).isEqualTo("summarize it");
			});
	}

	@Test
	void aTaskReadBackByIdCarriesTheRunErrorAsItsStatusMessage() {
		String taskId = taskId(rpc("""
				{"jsonrpc":"2.0","id":1,"method":"message/send","params":{"message":{"role":"user","kind":"message",\
				"messageId":"m-2","parts":[{"kind":"text","text":"go"}]}}}""").body());

		HttpResponse<String> response = rpc(
				"{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tasks/get\",\"params\":{\"id\":\"" + taskId
						+ "\",\"historyLength\":0}}");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("\"id\":\"" + taskId + "\"")
			.contains("does not exist")
			.contains("\"state\":\"failed\"");
	}

	/**
	 * The Jackson 3 default the spike named, measured through the real facade rather than
	 * asserted from the library's source: {@code TaskQueryParams.historyLength} is a
	 * primitive {@code int} and Jackson 3 ships {@code FAIL_ON_NULL_FOR_PRIMITIVES=true},
	 * so a client that omits the field fails inside the facade before any handler runs.
	 * The ruling is to document the field as required, not to relax a mapper this module
	 * does not own.
	 */
	@Test
	void omittingThePrimitiveBackedHistoryLengthFailsInsideTheFacadeBeforeTheHandlerRuns() {
		HttpResponse<String> withField = rpc(
				"{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tasks/get\",\"params\":{\"id\":\"no-such-run\","
						+ "\"historyLength\":0}}");
		HttpResponse<String> withoutField = rpc(
				"{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tasks/get\",\"params\":{\"id\":\"no-such-run\"}}");

		assertThat(withField.statusCode()).as("with the field the request reaches the handler").isEqualTo(200);
		assertThat(withoutField.statusCode()).as("omitted, it never reaches the handler").isEqualTo(500);
		assertThat(withoutField.body()).contains("historyLength").contains("Cannot map");
	}

	@Test
	void cancellingAFinishedTaskRefusesOverTheWire() {
		String taskId = taskId(rpc("""
				{"jsonrpc":"2.0","id":1,"method":"message/send","params":{"message":{"role":"user","kind":"message",\
				"messageId":"m-3","parts":[{"kind":"text","text":"go"}]}}}""").body());

		HttpResponse<String> response = rpc(
				"{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tasks/cancel\",\"params\":{\"id\":\"" + taskId + "\"}}");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("was not cancelled").contains("synchronous");
	}

	@Test
	void aMethodTheFacadeDoesNotDispatchIsAFiftyHundredRatherThanAProtocolErrorCode() {
		// Recorded deviation, measured: tasks/list has no 0.3 method for the facade to
		// route,
		// so a client asking for it gets the library's 500, not a section 9.5 error code.
		HttpResponse<String> response = rpc("{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tasks/list\",\"params\":{}}");

		assertThat(response.statusCode()).isEqualTo(500);
		assertThat(response.body()).contains("tasks/list is not supported");
	}

	private HttpResponse<String> rpc(String body) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + this.port + "/a2a"))
				.header("Content-Type", "application/json")
				.header("Accept", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();
			return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String taskId(String json) {
		int start = json.indexOf("\"id\":\"");
		if (start < 0) {
			throw new AssertionError("no task id in " + json);
		}
		int from = start + "\"id\":\"".length();
		return json.substring(from, json.indexOf('"', from));
	}

	private static Path createTraceDir() {
		try {
			return Files.createTempDirectory("smith-a2a-http-test-");
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
