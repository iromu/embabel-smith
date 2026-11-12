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

package com.embabel.smith.unified;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.embabel.smith.unified.dto.RemoteRequest;
import com.embabel.smith.unified.dto.RemoteResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Remote pane (POC 22): invoke a remote action over the network. It drives POC 16's
 * {@code RestAction} sidecar over its three-endpoint contract
 * ({@code GET /api/v1/actions} for reachability, {@code POST /api/v1/actions/execute} to
 * run the deterministic action) — the "normal agent harness" where a spec invokes an
 * external process. The MCP server (POC 13) is the alternative transport, reported
 * alongside via {@code mcpUrl}.
 *
 * <p>
 * Gated on the backing process: when the sidecar is unreachable the pane returns a
 * "backing process not running" body instead of crashing. The deterministic sidecar
 * action needs no model endpoint, so this pane is offline-capable once the sidecar is up.
 *
 * @author Ivan Rodriguez
 */
@RestController
public class RemoteController {

	private static final String ACTION_NAME = "remoteDigest";

	private final String sidecarUrl;

	private final String mcpUrl;

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

	private final ObjectMapper mapper = new ObjectMapper();

	public RemoteController(@Value("${builder.remote-sidecar-url:http://localhost:18090}") String sidecarUrl,
			@Value("${builder.remote-mcp-url:http://localhost:8080}") String mcpUrl) {
		this.sidecarUrl = sidecarUrl;
		this.mcpUrl = mcpUrl;
	}

	/**
	 * Invoke the remote action on the sidecar; a "not running" body when the sidecar is
	 * down.
	 * @param request the remote request (user input for the action).
	 * @return the remote response (action content, or a "not running" body).
	 */
	@PostMapping("/api/remote")
	public RemoteResponse remote(@RequestBody RemoteRequest request) {
		String body = String.format("{\"action_name\":\"%s\",\"parameters\":{\"userInput\":{\"content\":\"%s\"}}}",
				ACTION_NAME, escape(request.inputOrDefault()));
		HttpRequest httpRequest = HttpRequest.newBuilder()
			.uri(URI.create(this.sidecarUrl + "/api/v1/actions/execute"))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body))
			.timeout(Duration.ofSeconds(10))
			.build();
		try {
			HttpResponse<String> response = this.http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				return RemoteResponse.notRunning(
						"backing process not running (sidecar returned HTTP " + response.statusCode() + ")",
						this.mcpUrl);
			}
			JsonNode node = this.mapper.readTree(response.body());
			String content = node.path("content").asText("");
			return RemoteResponse.ok(ACTION_NAME, content, this.mcpUrl);
		}
		catch (Exception ex) {
			return RemoteResponse.notRunning(
					"backing process not running: " + ((ex.getMessage() == null) ? ex.toString() : ex.getMessage()),
					this.mcpUrl);
		}
	}

	private static String escape(String s) {
		return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
	}

}
