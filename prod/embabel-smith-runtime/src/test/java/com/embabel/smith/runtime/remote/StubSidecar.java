/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.remote;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * An in-process sidecar for the offline bridge tests: it serves the three contract
 * endpoints with responses a test can set, and records what it received so a test can
 * assert on the request the client actually sent rather than trusting the client's own
 * account of it. Follows the precedent {@code core/RuntimeCoreTest} sets for offline HTTP
 * in this module.
 */
final class StubSidecar implements AutoCloseable {

	private final HttpServer server;

	private final AtomicInteger requestCount = new AtomicInteger();

	private final List<String> requestLines = new ArrayList<>();

	private volatile String catalogBody = "[]";

	private volatile int catalogStatus = 200;

	private volatile String executeBody = "{\"content\":\"stub\"}";

	private volatile int executeStatus = 200;

	private volatile String lastExecuteRequestBody = "";

	private StubSidecar(HttpServer server) {
		this.server = server;
	}

	/**
	 * Starts the stub on an ephemeral port.
	 * @return the stub
	 * @throws IOException when the port cannot be bound
	 */
	static StubSidecar start() throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		StubSidecar sidecar = new StubSidecar(server);
		server.createContext("/", sidecar::route);
		server.start();
		return sidecar;
	}

	String baseUrl() {
		return "http://127.0.0.1:" + this.server.getAddress().getPort();
	}

	void respondToCatalog(int status, String body) {
		this.catalogStatus = status;
		this.catalogBody = body;
	}

	void respondToExecute(int status, String body) {
		this.executeStatus = status;
		this.executeBody = body;
	}

	/**
	 * How many requests the stub has answered, the assertion that nothing was sent to it.
	 */
	int requestCount() {
		return this.requestCount.get();
	}

	/**
	 * One line per request, {@code METHOD /path}, so a test can name the endpoint it
	 * reached.
	 */
	List<String> requestLines() {
		synchronized (this.requestLines) {
			return List.copyOf(this.requestLines);
		}
	}

	String lastExecuteRequestBody() {
		return this.lastExecuteRequestBody;
	}

	/**
	 * A URL whose port is bound by nobody, so connecting to it is refused rather than
	 * slow.
	 * @return a base URL that will not answer
	 */
	static String closedBaseUrl() throws IOException {
		try (ServerSocket socket = new ServerSocket(0)) {
			return "http://127.0.0.1:" + socket.getLocalPort();
		}
	}

	private void route(HttpExchange exchange) throws IOException {
		this.requestCount.incrementAndGet();
		String line = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath();
		synchronized (this.requestLines) {
			this.requestLines.add(line);
		}
		String path = exchange.getRequestURI().getPath();
		if (exchange.getRequestMethod().equals("GET") && path.equals(RemoteActionBridge.ACTIONS_PATH)) {
			respond(exchange, this.catalogStatus, this.catalogBody);
		}
		else if (exchange.getRequestMethod().equals("POST") && path.equals(RemoteActionBridge.EXECUTE_PATH)) {
			this.lastExecuteRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			respond(exchange, this.executeStatus, this.executeBody);
		}
		else {
			respond(exchange, 404, "{\"error\":\"unhandled " + path + "\"}");
		}
		exchange.close();
	}

	private static void respond(HttpExchange exchange, int status, String json) throws IOException {
		byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(status, bytes.length);
		try (OutputStream os = exchange.getResponseBody()) {
			os.write(bytes);
		}
	}

	@Override
	public void close() {
		this.server.stop(0);
	}

}
