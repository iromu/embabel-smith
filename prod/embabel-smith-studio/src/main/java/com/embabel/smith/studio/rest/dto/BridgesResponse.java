/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest.dto;

import java.util.List;

/**
 * The whole remote surface in one read: every configured bridge with its reachability,
 * the configuration entries that could not be read as {@code name@url}, and the MCP
 * endpoint.
 *
 * <p>
 * The MCP endpoint is reported and never probed. A bare HTTP request against a Streamable
 * HTTP endpoint answers in a way that does not mean the tools work, so showing a badge
 * for it would be a claim this product has not earned — the pane labels it configured and
 * not invoked.
 *
 * @param bridges every configured bridge, reachable or not, in configured order.
 * @param malformedBridges the raw entries that were not {@code name@url}, reported rather
 * than dropped or guessed.
 * @param mcpEndpoint the configured MCP endpoint, or null when none is configured. Never
 * probed.
 * @author Ivan Rodriguez
 */
public record BridgesResponse(List<BridgeInfo> bridges, List<String> malformedBridges, String mcpEndpoint) {
}
