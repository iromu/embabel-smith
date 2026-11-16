/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.unified.dto;

/**
 * The result of a remote invocation (POC 16): whether the backing process was reachable,
 * the action name, and its result. When the sidecar is down, {@link #running} is false
 * and {@link #error} names the not-running state (the pane does not crash).
 *
 * @param running Whether the backing process was reachable.
 * @param error The error naming the not-running state, or null.
 * @param action The action name.
 * @param result The action result.
 * @param mcpUrl The sidecar's MCP URL.
 * @author Ivan Rodriguez
 */
public record RemoteResponse(boolean running, String error, String action, String result, String mcpUrl) {

	public static RemoteResponse ok(String action, String result, String mcpUrl) {
		return new RemoteResponse(true, null, action, result, mcpUrl);
	}

	public static RemoteResponse notRunning(String error, String mcpUrl) {
		return new RemoteResponse(false, error, null, null, mcpUrl);
	}
}
