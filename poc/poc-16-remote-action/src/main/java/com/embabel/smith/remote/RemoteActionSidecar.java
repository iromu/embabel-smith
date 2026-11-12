/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.embabel.smith.remote;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * The remote-action sidecar (POC 16, doc 03 §2.3 bridge #2 / §4.2 Option B).
 *
 * <p>
 * A standalone HTTP server (JDK {@link HttpServer}, no extra dependencies) that exposes a
 * deterministic action over the three endpoints Embabel's {@code embabel-agent-remote}
 * client expects:
 * <ul>
 * <li>{@code GET  /api/v1/actions} — the action catalog ({@code RestActionMetadata}
 * JSON)</li>
 * <li>{@code GET  /api/v1/types} — the action's types (empty here: the action uses the
 * platform's built-in {@code UserInput}/{@code BackgroundMessage})</li>
 * <li>{@code POST /api/v1/actions/execute} — run the action; body {@code {action_name,
 * parameters}}, returns the output as a JSON object</li>
 * </ul>
 *
 * <p>
 * The action is deliberately <em>deterministic and LLM-free</em> (it just digests the
 * input text into word/char stats) so the POC shows the bridge executing <em>sidecar
 * code</em> that the agent itself has no way to run — the "normal agent harness" where a
 * spec invokes an external process.
 *
 * @author Ivan Rodriguez
 */
public class RemoteActionSidecar {

	/** The type name the sidecar's action consumes. */
	public static final String USER_INPUT = "com.embabel.agent.domain.io.UserInput";

	/** The type name the sidecar's action produces. */
	public static final String BACKGROUND_MESSAGE = "com.embabel.agent.spec.model.BackgroundMessage";

	/** The name of the sidecar's single exposed action. */
	public static final String ACTION_NAME = "remoteDigest";

	/** The JSON mapper used to parse requests and serialize responses. */
	private final ObjectMapper mapper = new ObjectMapper();

	/** The running HTTP server, or null when the sidecar is stopped. */
	private HttpServer server;

	/** The port the server is bound to (set on start). */
	private int port;

	/**
	 * The action catalog: one deterministic action consuming {@code UserInput}, producing
	 * {@code BackgroundMessage}.
	 * @return the action catalog JSON
	 */
	public String actionsJson() {
		return """
				[
				  {
				    "name": "remoteDigest",
				    "description": "Digest the input text on the sidecar (deterministic word/char stats, no LLM)",
				    "inputs":  [{"name": "userInput", "type": "com.embabel.agent.domain.io.UserInput"}],
				    "outputs": [{"name": "it",        "type": "com.embabel.agent.spec.model.BackgroundMessage"}],
				    "pre":   [],
				    "post":  [],
				    "cost": 1.0,
				    "value": 1.0,
				    "can_rerun": true
				  }
				]
				""";
	}

	/**
	 * Starts the sidecar on {@code port} (0 = ephemeral; the bound port is then available
	 * via {@link #port()}).
	 * @param requestedPort the port to bind, or 0 for an ephemeral one
	 */
	public synchronized void start(int requestedPort) throws IOException {
		this.server = HttpServer.create(new InetSocketAddress(requestedPort), 0);
		this.server.createContext("/", this::route);
		this.server.start();
		this.port = this.server.getAddress().getPort();
	}

	public synchronized void stop() {
		if (this.server != null) {
			this.server.stop(0);
		}
	}

	public int port() {
		return this.port;
	}

	public String baseUrl() {
		return "http://localhost:" + this.port;
	}

	private void route(HttpExchange exchange) throws IOException {
		String path = exchange.getRequestURI().getPath();
		String method = exchange.getRequestMethod();
		try {
			if (method.equals("GET") && path.equals("/api/v1/actions")) {
				respond(exchange, 200, actionsJson());
			}
			else if (method.equals("GET") && path.equals("/api/v1/types")) {
				respond(exchange, 200, "[]");
			}
			else if (method.equals("POST") && path.equals("/api/v1/actions/execute")) {
				respond(exchange, 200, execute(exchange));
			}
			else {
				respond(exchange, 404, "{\"error\":\"unknown endpoint \" + \"" + path + "\"}");
			}
		}
		catch (Exception ex) {
			respond(exchange, 500, "{\"error\":\"" + ex.getMessage() + "\"}");
		}
		finally {
			exchange.close();
		}
	}

	/**
	 * Runs the action: reads the input text from {@code parameters.input.content} and
	 * digests it.
	 * @param exchange the HTTP exchange carrying the execution request
	 * @return the action output as a JSON string
	 */
	private String execute(HttpExchange exchange) throws IOException {
		String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		JsonNode req = this.mapper.readTree(body);
		String name = req.path("action_name").asText("");
		if (!ACTION_NAME.equals(name)) {
			throw new IllegalArgumentException("unknown action: " + name);
		}
		JsonNode params = req.path("parameters");
		JsonNode input = params.path("userInput");
		String content = input.path("content").asText("");
		if (content.isBlank() && !input.isMissingNode()) {
			// Fall back to a bare-string input if the value was not nested under
			// `content`.
			content = input.isTextual() ? input.asText() : input.toString();
		}

		int words = content.isBlank() ? 0 : content.trim().split("\\s+").length;
		int chars = content.length();
		String digest = "Digested on the remote sidecar (no LLM): " + words + " words, " + chars
				+ " chars. Input began: \"" + firstWord(content) + "\"";

		// The output is a BackgroundMessage-shaped object (content + timestamp).
		return this.mapper
			.writeValueAsString(Map.of("content", digest, "timestamp", Instant.now().toString()));
	}

	private String firstWord(String s) {
		String t = s.trim();
		return t.isEmpty() ? "" : t.split("\\s+")[0];
	}

	private void respond(HttpExchange exchange, int status, String json) throws IOException {
		byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(status, bytes.length);
		try (OutputStream os = exchange.getResponseBody()) {
			os.write(bytes);
		}
	}

}
