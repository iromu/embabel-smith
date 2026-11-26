/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.a2a;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.embabel.agent.a2a.server.A2ARequestHandler;
import com.embabel.smith.common.trace.TraceRecord;
import com.embabel.smith.common.trace.TraceStore;
import com.embabel.smith.runtime.AgentSpecRunService;
import com.embabel.smith.runtime.AgentSpecRunService.RunResult;
import com.embabel.smith.runtime.core.TrustTier;
import io.a2a.spec.Artifact;
import io.a2a.spec.CancelTaskRequest;
import io.a2a.spec.CancelTaskResponse;
import io.a2a.spec.GetTaskRequest;
import io.a2a.spec.GetTaskResponse;
import io.a2a.spec.InternalError;
import io.a2a.spec.InvalidRequestError;
import io.a2a.spec.JSONRPCErrorResponse;
import io.a2a.spec.JSONRPCResponse;
import io.a2a.spec.Message;
import io.a2a.spec.MessageSendParams;
import io.a2a.spec.NonStreamingJSONRPCRequest;
import io.a2a.spec.SendMessageRequest;
import io.a2a.spec.SendMessageResponse;
import io.a2a.spec.SendStreamingMessageRequest;
import io.a2a.spec.SendStreamingMessageResponse;
import io.a2a.spec.StreamingJSONRPCRequest;
import io.a2a.spec.Task;
import io.a2a.spec.TaskArtifactUpdateEvent;
import io.a2a.spec.TaskIdParams;
import io.a2a.spec.TaskNotFoundError;
import io.a2a.spec.TaskQueryParams;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatus;
import io.a2a.spec.TaskStatusUpdateEvent;
import io.a2a.spec.TextPart;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The A2A request handler behind the runtime's A2A endpoint: one request is one
 * {@link AgentSpecRunService} run, and one run is one task whose id is the run's
 * {@link TraceRecord#runId()}.
 *
 * <p>
 * This replaces the library's {@code AutonomyA2ARequestHandler} on purpose. Running the
 * goal through {@code AgentSpecRunService} is what makes an A2A run the same run a
 * {@code smith
 * run} or a studio call makes: the same trust-tier gate, the same planner selection, and
 * the same trace record the self-improvement loop reads. The library still owns every
 * request and response type on the wire; only the routing is ours.
 *
 * <p>
 * Served operations, and nothing else: {@code message/send}, {@code message/stream},
 * {@code tasks/get}, {@code tasks/cancel}. The facade in front of this handler rejects
 * {@code tasks/list} before reaching it, so listing is not offered.
 *
 * <p>
 * <b>Where the run parameters come from.</b> The protocol request carries no spec source
 * and no trust tier, so both are configured for the endpoint once
 * ({@code smith.a2a.source}, {@code smith.a2a.trust-tier}). The goal and the planner are
 * per-request and travel in the message-send {@code metadata} under {@code goal} and
 * {@code planner}; omitted, the runtime's own rule applies (a source exporting a single
 * goal needs no {@code goal}, and a source exporting several names one or fails).
 *
 * <p>
 * <b>Terminal only.</b> A run is synchronous, so every record in the trace store is
 * already terminal: {@code tasks/get} never reports a WORKING task, and
 * {@code tasks/cancel} can never stop anything. It says so rather than pretending to.
 *
 * @author Ivan Rodriguez
 */
public class RunBackedA2ARequestHandler implements A2ARequestHandler {

	/** The message-send metadata key naming the goal to run. */
	public static final String GOAL_KEY = "goal";

	/** The message-send metadata key naming the planner to run it with. */
	public static final String PLANNER_KEY = "planner";

	private static final String ARTIFACT_NAME = "output";

	/**
	 * A GOAP run makes model calls and can take tens of seconds; the stream outlives
	 * that.
	 */
	private static final long STREAM_TIMEOUT_MILLIS = 10 * 60 * 1000L;

	private final AgentSpecRunService runService;

	private final TraceStore traceStore;

	private final String sourceUri;

	private final TrustTier tier;

	/**
	 * Creates the handler.
	 * @param runService the run service an A2A request drives
	 * @param traceStore the store every run appends to and that {@code tasks/get} reads
	 * @param sourceUri the spec source every run executes against
	 * @param tier the trust tier every run executes under
	 */
	public RunBackedA2ARequestHandler(AgentSpecRunService runService, TraceStore traceStore, String sourceUri,
			TrustTier tier) {
		this.runService = runService;
		this.traceStore = traceStore;
		this.sourceUri = sourceUri;
		this.tier = tier;
	}

	@Override
	public JSONRPCResponse<?> handleJsonRpc(NonStreamingJSONRPCRequest<?> request) {
		if (request instanceof SendMessageRequest send) {
			return messageSend(send);
		}
		if (request instanceof GetTaskRequest get) {
			return tasksGet(get);
		}
		if (request instanceof CancelTaskRequest cancel) {
			return tasksCancel(cancel);
		}
		return new JSONRPCErrorResponse(request.getId(),
				new InvalidRequestError("Unsupported A2A method: " + request.getMethod()));
	}

	/**
	 * Serves {@code message/stream} by running the request and emitting what the run
	 * produced: the task, then its artifacts, then the terminal status update, then
	 * close. No per-step progress is emitted because the run does not report any — the
	 * steps exist only after it completes, and inventing WORKING events would put events
	 * on the wire the run never produced.
	 * @param request the streaming request, expected to be {@code message/stream}
	 * @return the emitter carrying the run's events, already completed
	 */
	@Override
	public SseEmitter handleJsonRpcStream(StreamingJSONRPCRequest<?> request) {
		if (!(request instanceof SendStreamingMessageRequest stream)) {
			throw new UnsupportedOperationException(
					"Only " + SendStreamingMessageRequest.METHOD + " is streamed, not " + request.getMethod());
		}
		SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
		MessageSendParams params = stream.getParams();
		RunResult result = run(params);
		try {
			TraceRecord trace = result.trace();
			if (trace == null) {
				emitter.send(new SendStreamingMessageResponse(stream.getId(), noTraceError(result)));
			}
			else {
				Task task = task(trace.runId(), result.success(), output(result, trace), result.error());
				emitter.send(new SendStreamingMessageResponse(stream.getId(), task));
				for (Artifact artifact : task.getArtifacts()) {
					emitter
						.send(new SendStreamingMessageResponse(stream.getId(), new TaskArtifactUpdateEvent(task.getId(),
								artifact, task.getContextId(), null, null, null)));
				}
				emitter.send(new SendStreamingMessageResponse(stream.getId(),
						new TaskStatusUpdateEvent(task.getId(), task.getStatus(), task.getContextId(), true, null)));
			}
			emitter.complete();
		}
		catch (IOException ex) {
			emitter.completeWithError(ex);
		}
		return emitter;
	}

	private JSONRPCResponse<?> messageSend(SendMessageRequest request) {
		RunResult result = run(request.getParams());
		TraceRecord trace = result.trace();
		if (trace == null) {
			return new JSONRPCErrorResponse(request.getId(), new InternalError(noTraceError(result).getMessage()));
		}
		return new SendMessageResponse(request.getId(),
				task(trace.runId(), result.success(), output(result, trace), result.error()));
	}

	private JSONRPCResponse<?> tasksGet(GetTaskRequest request) {
		TaskQueryParams params = request.getParams();
		Optional<TraceRecord> record = findRun(params.id());
		if (record.isEmpty()) {
			return new GetTaskResponse(request.getId(), taskNotFound(params.id()));
		}
		TraceRecord run = record.get();
		return new GetTaskResponse(request.getId(), task(run.runId(), run.success(), run.outputExcerpt(), run.error()));
	}

	/**
	 * Refuses to cancel. The store only ever holds finished runs — the trace record is
	 * written after the run returns — so there is no running plan here to stop, and the
	 * status message says that instead of claiming one was stopped.
	 * @param request the cancel request naming the task
	 * @return the task in its real terminal state with the refusal as its status message,
	 * or a task-not-found error when no run has that id
	 */
	private JSONRPCResponse<?> tasksCancel(CancelTaskRequest request) {
		TaskIdParams params = request.getParams();
		Optional<TraceRecord> record = findRun(params.id());
		if (record.isEmpty()) {
			return new CancelTaskResponse(request.getId(), taskNotFound(params.id()));
		}
		TraceRecord run = record.get();
		String refusal = "Task " + params.id() + " is already " + (run.success() ? "COMPLETED" : "FAILED")
				+ " and was not cancelled: the run is synchronous, so by the time it is recorded it has already"
				+ " returned and there is no plan left to stop.";
		TaskStatus status = new TaskStatus(run.success() ? TaskState.COMPLETED : TaskState.FAILED,
				agentMessage(params.id(), refusal), null);
		return new CancelTaskResponse(request.getId(), new Task(run.runId(), run.runId(), status,
				artifacts(run.runId(), run.success(), run.outputExcerpt()), List.of(), null));
	}

	private RunResult run(MessageSendParams params) {
		return this.runService.run(this.sourceUri, metadataValue(params.metadata(), GOAL_KEY),
				inputText(params.message()), metadataValue(params.metadata(), PLANNER_KEY), this.tier);
	}

	private Optional<TraceRecord> findRun(String taskId) {
		return this.traceStore.read().records().stream().filter((record) -> taskId.equals(record.runId())).findFirst();
	}

	/**
	 * The task a run is: COMPLETED with the output as an artifact, or FAILED carrying the
	 * run's error as its status message. The task id is the run id, and the context id is
	 * the run id too — one run is one task, so there is nothing else for a context to
	 * group.
	 * @param runId the run's trace id, which is the task id
	 * @param success whether the goal was achieved
	 * @param output the goal output, or its excerpt when read back from the trace
	 * @param error the failure reason
	 * @return the task
	 */
	private Task task(String runId, boolean success, String output, String error) {
		TaskStatus status = (success) ? new TaskStatus(TaskState.COMPLETED, null, null)
				: new TaskStatus(TaskState.FAILED, agentMessage(runId, error), null);
		return new Task(runId, runId, status, artifacts(runId, success, output), List.of(), null);
	}

	private List<Artifact> artifacts(String runId, boolean success, String output) {
		if (!success || output == null || output.isBlank()) {
			return List.of();
		}
		return List.of(new Artifact(runId + "-" + ARTIFACT_NAME, ARTIFACT_NAME, null, List.of(new TextPart(output)),
				null, null));
	}

	private Message agentMessage(String taskId, String text) {
		return new Message(Message.Role.AGENT, List.of(new TextPart((text != null) ? text : "")), taskId, taskId,
				taskId, null, null, null);
	}

	private static String inputText(Message message) {
		List<String> texts = new ArrayList<>();
		for (var part : message.getParts()) {
			if (part instanceof TextPart text) {
				texts.add(text.getText());
			}
		}
		return String.join("\n", texts);
	}

	private static String metadataValue(Map<String, Object> metadata, String key) {
		Object value = (metadata != null) ? metadata.get(key) : null;
		return (value != null) ? value.toString() : null;
	}

	private static String output(RunResult result, TraceRecord trace) {
		return (result.output() != null) ? result.output() : trace.outputExcerpt();
	}

	private static InternalError noTraceError(RunResult result) {
		return new InternalError("The run finished but wrote no trace record, so it has no task id"
				+ ((result.error() != null) ? "; run error was: " + result.error() : ""));
	}

	private static TaskNotFoundError taskNotFound(String taskId) {
		return new TaskNotFoundError(null, "No run recorded with task id " + taskId, null);
	}

}
