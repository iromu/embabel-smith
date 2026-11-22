/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest;

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
 * An in-process sidecar for the Remote endpoint tests, serving the three contract
 * endpoints with responses a test sets and counting what it received. The count is the
 * point: it is how a test proves a refusal sent nothing, rather than merely asserting
 * that the response looked like a refusal.
 */
final class StubSidecar implements AutoCloseable {

	private static final String ACTIONS_PATH = "/api/v1/actions";

	private static final String EXECUTE_PATH = "/api/v1/actions/execute";

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

	int requestCount() {
		return this.requestCount.get();
	}

	List<String> requestLines() {
		synchronized (this.requestLines) {
			return List.copyOf(this.requestLines);
		}
	}

	String lastExecuteRequestBody() {
		return this.lastExecuteRequestBody;
	}

	/**
	 * A base URL bound by nobody, so a bridge pointed at it is a process that is not
	 * running.
	 */
	static String closedBaseUrl() throws IOException {
		try (ServerSocket socket = new ServerSocket(0)) {
			return "http://127.0.0.1:" + socket.getLocalPort();
		}
	}

	private void route(HttpExchange exchange) throws IOException {
		this.requestCount.incrementAndGet();
		String path = exchange.getRequestURI().getPath();
		String method = exchange.getRequestMethod();
		synchronized (this.requestLines) {
			this.requestLines.add(method + " " + path);
		}
		if (method.equals("GET") && path.equals(ACTIONS_PATH)) {
			respond(exchange, this.catalogStatus, this.catalogBody);
		}
		else if (method.equals("POST") && path.equals(EXECUTE_PATH)) {
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
