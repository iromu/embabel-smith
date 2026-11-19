/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.remote;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The product's client for a remote-action sidecar, speaking the three-endpoint contract
 * POC 16 documented: {@code GET /api/v1/actions} for the catalog,
 * {@code GET /api/v1/types} for the types it needs, {@code POST /api/v1/actions/execute}
 * to run one action. It is plain {@link HttpClient}, so no remote library enters the
 * product; the planner-side {@code embabel-agent-remote} wiring stays a separate change.
 *
 * <p>
 * Two rules shape every method here. Reachability is read from the <em>response</em> — a
 * 2xx whose body is not a JSON array is unreachable, so a process that answers with
 * something else is never reported as working. And a sidecar that does not answer is a
 * returned value, never a thrown exception, because an absent process is a state a pane
 * has to display.
 *
 * @author Ivan Rodriguez
 */
public class RemoteActionBridge {

	/**
	 * The catalog endpoint, whose answer decides whether a bridge counts as reachable.
	 */
	public static final String ACTIONS_PATH = "/api/v1/actions";

	/** The execute endpoint, posted {@code {action_name, parameters}}. */
	public static final String EXECUTE_PATH = "/api/v1/actions/execute";

	/**
	 * How long to wait for a socket, which is what a machine that is simply off costs.
	 */
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);

	/** How long to wait for an answer once connected, the bound on a slow sidecar. */
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

	private final RemoteBridge bridge;

	private final HttpClient http;

	private final ObjectMapper mapper = new ObjectMapper();

	public RemoteActionBridge(RemoteBridge bridge) {
		this.bridge = bridge;
		this.http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
	}

	/**
	 * Ask the bridge for its catalog and decide from the answer whether it is running.
	 * @return the bridge state — reachable with the actions it advertised, or unreachable
	 * with the URL probed and the failure
	 */
	public BridgeState probe() {
		String url = this.bridge.baseUrl() + ACTIONS_PATH;
		try {
			HttpResponse<String> response = this.http.send(get(url), HttpResponse.BodyHandlers.ofString());
			int status = response.statusCode();
			if (!isSuccess(status)) {
				return BridgeState.unreachable(this.bridge, url, "answered HTTP " + status);
			}
			JsonNode catalog = this.mapper.readTree(response.body());
			if (!catalog.isArray()) {
				return BridgeState.unreachable(this.bridge, url, "answered a catalog that is not a JSON array");
			}
			return BridgeState.reachable(this.bridge, url, actions(catalog));
		}
		catch (Exception ex) {
			return BridgeState.unreachable(this.bridge, url, failure(ex));
		}
	}

	/**
	 * Run one action, sending the text as its {@code UserInput} content. The action name
	 * is sent as the caller named it — the sidecar decides whether it exists, and its
	 * answer is what comes back.
	 * @param actionName the action to run, as the sidecar's catalog named it
	 * @param input the operator's text, sent as {@code parameters.userInput.content}
	 * @return the output the sidecar produced, its refusal, or the failure that reached
	 * no process
	 */
	public ExecuteResult execute(String actionName, String input) {
		String url = this.bridge.baseUrl() + EXECUTE_PATH;
		try {
			HttpResponse<String> response = this.http.send(post(url, executeBody(actionName, input)),
					HttpResponse.BodyHandlers.ofString());
			int status = response.statusCode();
			if (!isSuccess(status)) {
				return ExecuteResult.refused(url, status, response.body());
			}
			return ExecuteResult.executed(url, this.mapper.readTree(response.body()).path("content").asText(""));
		}
		catch (Exception ex) {
			return ExecuteResult.unreachable(url, failure(ex));
		}
	}

	/**
	 * The contract's execute body. Built with the mapper rather than by splicing text
	 * into a template, so quotes and newlines in the operator's input cannot break out of
	 * the JSON they were put in.
	 * @param actionName the action to run
	 * @param input the text to send as its {@code UserInput} content
	 * @return the request body
	 * @throws JsonProcessingException when the body cannot be written
	 */
	private String executeBody(String actionName, String input) throws JsonProcessingException {
		ObjectNode body = this.mapper.createObjectNode();
		body.put("action_name", actionName);
		body.putObject("parameters").putObject("userInput").put("content", input);
		return this.mapper.writeValueAsString(body);
	}

	private static List<AdvertisedAction> actions(JsonNode catalog) {
		List<AdvertisedAction> actions = new ArrayList<>();
		for (JsonNode entry : catalog) {
			actions.add(new AdvertisedAction(entry.path("name").asText(""), entry.path("description").asText(""),
					inputTypeNames(entry.path("inputs"))));
		}
		return actions;
	}

	private static List<String> inputTypeNames(JsonNode inputs) {
		List<String> names = new ArrayList<>();
		if (inputs.isArray()) {
			for (JsonNode input : inputs) {
				names.add(input.path("type").asText(""));
			}
		}
		return names;
	}

	private HttpRequest get(String url) {
		return HttpRequest.newBuilder().uri(URI.create(url)).timeout(REQUEST_TIMEOUT).GET().build();
	}

	private HttpRequest post(String url, String body) {
		return HttpRequest.newBuilder()
			.uri(URI.create(url))
			.header("Content-Type", "application/json")
			.timeout(REQUEST_TIMEOUT)
			.POST(HttpRequest.BodyPublishers.ofString(body))
			.build();
	}

	private static boolean isSuccess(int status) {
		return status >= 200 && status < 300;
	}

	// The JDK's ConnectException carries no message on this JVM, so the exception's own
	// name is the
	// reason kept — it is what distinguishes a host that refused the socket from one that
	// answered
	// with something that was not a catalog.
	private static String failure(Exception ex) {
		String message = ex.getMessage();
		return (message == null || message.isBlank()) ? ex.getClass().getSimpleName()
				: ex.getClass().getSimpleName() + ": " + message;
	}

}
