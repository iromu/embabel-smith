/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.a2a;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The endpoint as a client sees it: requests over HTTP against a running context, and the
 * response parsed with the same Jackson 3 mapper the library's facade serializes with, so
 * assertions are made on the wire document rather than on a re-encoding of it.
 */
final class A2aClient {

	private static final ObjectMapper JSON = new ObjectMapper();

	private final int port;

	A2aClient(int port) {
		this.port = port;
	}

	HttpResponse<String> get(String path) {
		return send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + this.port + path))
			.header("Accept", "application/json")
			.GET());
	}

	/**
	 * Posts a JSON-RPC envelope to the endpoint's POST path.
	 * @param body the envelope
	 * @return the response
	 */
	HttpResponse<String> rpc(String body) {
		return post(body, "application/json");
	}

	/**
	 * Posts a JSON-RPC envelope asking for a stream, which is what makes the endpoint
	 * answer with server-sent events.
	 * @param body the envelope
	 * @return the response, whose body is the event stream
	 */
	HttpResponse<String> streaming(String body) {
		return post(body, "text/event-stream");
	}

	private HttpResponse<String> post(String body, String accept) {
		return send(HttpRequest
			.newBuilder(URI.create("http://127.0.0.1:" + this.port + "/" + A2aConfiguration.ENDPOINT_PATH))
			.header("Content-Type", "application/json")
			.header("Accept", accept)
			.POST(HttpRequest.BodyPublishers.ofString(body)));
	}

	private static HttpResponse<String> send(HttpRequest.Builder request) {
		try {
			return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	static JsonNode json(String body) {
		try {
			return JSON.readTree(body);
		}
		catch (RuntimeException ex) {
			throw new AssertionError("Not JSON: " + body, ex);
		}
	}

	static List<String> events(String streamBody) {
		return streamBody.lines().filter((line) -> line.startsWith("data:")).map((line) -> line.substring(5)).toList();
	}

	static List<String> textList(JsonNode array) {
		List<String> values = new ArrayList<>();
		array.forEach((element) -> values.add(element.asText()));
		return values;
	}

}
