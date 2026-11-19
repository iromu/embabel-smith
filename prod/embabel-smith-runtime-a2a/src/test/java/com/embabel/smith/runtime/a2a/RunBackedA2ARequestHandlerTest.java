/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.a2a;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.smith.runtime.AgentSpecRunService;
import com.embabel.smith.runtime.AgentSpecRunService.RunResult;
import com.embabel.smith.runtime.core.TrustTier;
import com.embabel.smith.runtime.trace.JsonlTraceStore;
import com.embabel.smith.runtime.trace.TraceRecord;
import io.a2a.spec.Artifact;
import io.a2a.spec.CancelTaskRequest;
import io.a2a.spec.CancelTaskResponse;
import io.a2a.spec.GetTaskRequest;
import io.a2a.spec.GetTaskResponse;
import io.a2a.spec.JSONRPCErrorResponse;
import io.a2a.spec.JSONRPCResponse;
import io.a2a.spec.Message;
import io.a2a.spec.MessageSendParams;
import io.a2a.spec.Part;
import io.a2a.spec.SendMessageRequest;
import io.a2a.spec.SendMessageResponse;
import io.a2a.spec.Task;
import io.a2a.spec.TaskIdParams;
import io.a2a.spec.TaskNotFoundError;
import io.a2a.spec.TaskQueryParams;
import io.a2a.spec.TaskState;
import io.a2a.spec.TextPart;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Offline tests of the run-backed handler: what a request drives, and how the run comes
 * back as a task. The run service is stubbed for the translation tests, and the real one
 * is driven against a source that cannot resolve so the trace contract is exercised with
 * no endpoint.
 */
class RunBackedA2ARequestHandlerTest {

	private static final String SOURCE = "folder:/tmp/a2a-library";

	// --- message/send: what the request drives (task 3.1)

	@Test
	void aSendRequestRunsTheNamedGoalWithTheMessageTextAndTheConfiguredTier(@TempDir Path tmp) {
		AgentSpecRunService runService = stubRunService(
				new RunResult(true, "the summary", null, record("run-42", true, null, "the summary")));
		RunBackedA2ARequestHandler handler = new RunBackedA2ARequestHandler(runService, store(tmp), SOURCE,
				TrustTier.HIGH);

		handler.handleJsonRpc(sendRequest("Summarize the quarterly report",
				Map.of(RunBackedA2ARequestHandler.GOAL_KEY, "explainDone")));

		verify(runService).run(SOURCE, "explainDone", "Summarize the quarterly report", null, TrustTier.HIGH);
	}

	@Test
	void thePlannerMetadataReachesTheRunAndAnAbsentGoalDoesNot(@TempDir Path tmp) {
		AgentSpecRunService runService = stubRunService(
				new RunResult(true, "out", null, record("run-43", true, null, "out")));
		RunBackedA2ARequestHandler handler = new RunBackedA2ARequestHandler(runService, store(tmp), SOURCE,
				TrustTier.STANDARD);

		handler.handleJsonRpc(sendRequest("input", Map.of(RunBackedA2ARequestHandler.PLANNER_KEY, "GOAP")));

		verify(runService).run(SOURCE, null, "input", "GOAP", TrustTier.STANDARD);
	}

	// --- RunResult to Task (task 3.2)

	@Test
	void aSuccessfulRunIsACompletedTaskCarryingTheOutputAsAnArtifact(@TempDir Path tmp) {
		RunBackedA2ARequestHandler handler = handlerReturning(tmp,
				new RunResult(true, "The report says revenue rose.", null, record("run-1", true, null, "excerpt")));

		Task task = sentTask(handler.handleJsonRpc(sendRequest("go", Map.of())));

		assertThat(task.getStatus().state()).isEqualTo(TaskState.COMPLETED);
		assertThat(task.getArtifacts()).satisfiesExactly((Artifact artifact) -> {
			assertThat(artifact.parts()).satisfiesExactly(
					(part) -> assertThat(((TextPart) part).getText()).isEqualTo("The report says revenue rose."));
			// The artifact is the run's output, not the trace's excerpt.
			assertThat(artifact.artifactId()).isNotBlank();
		});
	}

	@Test
	void aGoalNotFoundRunIsAFailedTaskNamingTheReason(@TempDir Path tmp) {
		RunBackedA2ARequestHandler handler = handlerReturning(tmp,
				new RunResult(false, null, "Goal 'ghostDone' not achieved (status=STUCK)",
						record("run-2", false, "Goal 'ghostDone' not achieved", null)));

		Task task = sentTask(handler.handleJsonRpc(sendRequest("go", Map.of())));

		assertThat(task.getStatus().state()).isEqualTo(TaskState.FAILED);
		assertThat(partText(task)).contains("Goal 'ghostDone' not achieved (status=STUCK)");
		assertThat(task.getArtifacts()).isEmpty();
	}

	@Test
	void aTrustTierBlockIsAFailedTaskNamingTheTierAndTheStepItBlocked(@TempDir Path tmp) {
		String block = "Run blocked by trust tier 'standard': step 'runCli' requires tool group 'shell'";
		RunBackedA2ARequestHandler handler = handlerReturning(tmp,
				new RunResult(false, null, block, record("run-3", false, block, null)));

		Task task = sentTask(handler.handleJsonRpc(sendRequest("go", Map.of())));

		assertThat(task.getStatus().state()).isEqualTo(TaskState.FAILED);
		assertThat(partText(task)).isEqualTo(block);
	}

	// --- task identity (task 3.3)

	@Test
	void theTaskIdIsTheRunIdOfTheEmittedTraceRecord(@TempDir Path tmp) {
		// The real service, driven against a source that cannot resolve: it still appends
		// a
		// record, so the id the task carries is the one the store holds.
		JsonlTraceStore store = store(tmp);
		AgentSpecRunService runService = new AgentSpecRunService(mock(AgentPlatform.class), store);
		RunBackedA2ARequestHandler handler = new RunBackedA2ARequestHandler(runService, store,
				"folder:" + tmp.resolve("does-not-exist"), TrustTier.STANDARD);

		Task task = sentTask(handler.handleJsonRpc(sendRequest("go", Map.of())));

		assertThat(store.read().records()).hasSize(1);
		assertThat(task.getId()).isEqualTo(store.read().records().get(0).runId());
	}

	@Test
	void aRunThatWroteNoTraceIsANamedFailureRatherThanATaskWithoutAnId(@TempDir Path tmp) {
		// AgentSpecRunService returns a null trace when the write fails; a task with no
		// id
		// would be unqueryable, so the failure is reported as itself.
		RunBackedA2ARequestHandler handler = handlerReturning(tmp,
				new RunResult(false, null, "trace dir unwritable", null));

		JSONRPCResponse<?> response = handler.handleJsonRpc(sendRequest("go", Map.of()));

		assertThat(response).isInstanceOf(JSONRPCErrorResponse.class);
		JSONRPCErrorResponse error = (JSONRPCErrorResponse) response;
		assertThat(error.getError().getMessage()).contains("no trace record").contains("trace dir unwritable");
	}

	// --- one request, one trace record (task 3.4)

	@Test
	void oneSendRequestAppendsExactlyOneTraceRecordCarryingTheTrustTier(@TempDir Path tmp) {
		JsonlTraceStore store = store(tmp);
		AgentSpecRunService runService = new AgentSpecRunService(mock(AgentPlatform.class), store);
		RunBackedA2ARequestHandler handler = new RunBackedA2ARequestHandler(runService, store,
				"folder:" + tmp.resolve("does-not-exist"), TrustTier.HIGH);

		handler.handleJsonRpc(sendRequest("the input", Map.of(RunBackedA2ARequestHandler.GOAL_KEY, "explainDone")));

		assertThat(store.read().records()).satisfiesExactly((TraceRecord record) -> {
			assertThat(record.trustTier()).isEqualTo("high");
			assertThat(record.goal()).isEqualTo("explainDone");
			assertThat(record.input()).isEqualTo("the input");
			// A policy block and a bad spec stay distinguishable: both are recorded runs.
			assertThat(record.success()).isFalse();
		});
	}

	// --- tasks/get from the trace store (task 3.5)

	@Test
	void tasksGetReadsTheRecordForTheTaskId(@TempDir Path tmp) {
		JsonlTraceStore store = store(tmp);
		store.append(record("run-77", true, null, "the recorded excerpt"));
		RunBackedA2ARequestHandler handler = new RunBackedA2ARequestHandler(noRunService(), store, SOURCE,
				TrustTier.STANDARD);

		GetTaskResponse response = (GetTaskResponse) handler
			.handleJsonRpc(new GetTaskRequest(7, new TaskQueryParams("run-77", 0)));

		assertThat(response.getError()).isNull();
		Task task = response.getResult();
		assertThat(task.getId()).isEqualTo("run-77");
		assertThat(task.getStatus().state()).isEqualTo(TaskState.COMPLETED);
		assertThat(task.getArtifacts()).satisfiesExactly(
				(Artifact artifact) -> assertThat(partText(artifact.parts())).isEqualTo("the recorded excerpt"));
	}

	@Test
	void aFailedRunIsAFailedTaskWhenReadBack(@TempDir Path tmp) {
		JsonlTraceStore store = store(tmp);
		store.append(record("run-78", false, "Goal 'x' not achieved", null));
		RunBackedA2ARequestHandler handler = new RunBackedA2ARequestHandler(noRunService(), store, SOURCE,
				TrustTier.STANDARD);

		GetTaskResponse response = (GetTaskResponse) handler
			.handleJsonRpc(new GetTaskRequest(8, new TaskQueryParams("run-78", 0)));

		assertThat(response.getResult().getStatus().state()).isEqualTo(TaskState.FAILED);
		assertThat(partText(response.getResult().getStatus().message().getParts())).contains("Goal 'x' not achieved");
	}

	@Test
	void anUnknownTaskIdIsATaskNotFoundError(@TempDir Path tmp) {
		RunBackedA2ARequestHandler handler = new RunBackedA2ARequestHandler(noRunService(), store(tmp), SOURCE,
				TrustTier.STANDARD);

		GetTaskResponse response = (GetTaskResponse) handler
			.handleJsonRpc(new GetTaskRequest(9, new TaskQueryParams("never-ran", 0)));

		assertThat(response.getError()).isInstanceOf(TaskNotFoundError.class);
		assertThat(response.getError().getCode()).isEqualTo(TaskNotFoundError.DEFAULT_CODE);
	}

	// --- tasks/cancel is honest about a synchronous run (task 3.5)

	@Test
	void cancellingAFinishedRunRefusesAndSaysWhyInsteadOfClaimingAStop(@TempDir Path tmp) {
		JsonlTraceStore store = store(tmp);
		store.append(record("run-81", true, null, "done"));
		RunBackedA2ARequestHandler handler = new RunBackedA2ARequestHandler(noRunService(), store, SOURCE,
				TrustTier.STANDARD);

		CancelTaskResponse response = (CancelTaskResponse) handler
			.handleJsonRpc(new CancelTaskRequest(11, new TaskIdParams("run-81")));

		Task task = response.getResult();
		// The run already finished: the task keeps its real terminal state.
		assertThat(task.getStatus().state()).isEqualTo(TaskState.COMPLETED);
		assertThat(partText(task.getStatus().message().getParts())).contains("was not cancelled")
			.contains("synchronous");
	}

	@Test
	void cancellingAnUnknownTaskIdIsATaskNotFoundError(@TempDir Path tmp) {
		RunBackedA2ARequestHandler handler = new RunBackedA2ARequestHandler(noRunService(), store(tmp), SOURCE,
				TrustTier.STANDARD);

		CancelTaskResponse response = (CancelTaskResponse) handler
			.handleJsonRpc(new CancelTaskRequest(12, new TaskIdParams("never-ran")));

		assertThat(response.getError()).isInstanceOf(TaskNotFoundError.class);
	}

	// --- helpers

	private static JsonlTraceStore store(Path tmp) {
		return new JsonlTraceStore(tmp.resolve("traces/runs.jsonl"));
	}

	private static RunBackedA2ARequestHandler handlerReturning(Path tmp, RunResult result) {
		return new RunBackedA2ARequestHandler(stubRunService(result), store(tmp), SOURCE, TrustTier.STANDARD);
	}

	private static AgentSpecRunService stubRunService(RunResult result) {
		AgentSpecRunService runService = mock(AgentSpecRunService.class);
		when(runService.run(any(), any(), any(), any(), any())).thenReturn(result);
		return runService;
	}

	private static AgentSpecRunService noRunService() {
		return mock(AgentSpecRunService.class);
	}

	private static SendMessageRequest sendRequest(String text, Map<String, Object> metadata) {
		Message message = new Message(Message.Role.USER, List.of(new TextPart(text)), "msg-1", null, null, null, null,
				null);
		return new SendMessageRequest(1, new MessageSendParams(message, null, metadata));
	}

	private static Task sentTask(JSONRPCResponse<?> response) {
		assertThat(response).isInstanceOf(SendMessageResponse.class);
		assertThat(((SendMessageResponse) response).getError()).isNull();
		assertThat(((SendMessageResponse) response).getResult()).isInstanceOf(Task.class);
		return (Task) ((SendMessageResponse) response).getResult();
	}

	private static String partText(List<Part<?>> parts) {
		assertThat(parts).isNotEmpty();
		return ((TextPart) parts.get(0)).getText();
	}

	private static String partText(Task task) {
		Message message = task.getStatus().message();
		assertThat(message).as("a failed task carries its reason as a status message").isNotNull();
		return partText(message.getParts());
	}

	private static TraceRecord record(String runId, boolean success, String error, String outputExcerpt) {
		return new TraceRecord(runId, Instant.parse("2026-11-19T10:00:00Z"),
				new TraceRecord.SourceRef(SOURCE, null, null), new TraceRecord.PackRef("explain", "sha256:abc"),
				"explainDone", "the input", "GOAP", "standard", List.of(), success, error, outputExcerpt);
	}

}
