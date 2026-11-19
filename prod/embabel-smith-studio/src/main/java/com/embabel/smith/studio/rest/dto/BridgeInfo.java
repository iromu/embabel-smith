/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest.dto;

import java.util.List;

/**
 * One configured bridge with what a probe established about it. An unreachable bridge is
 * carried here rather than thrown, so the pane renders a dead process as a row state —
 * with the URL that was probed — instead of a failed request.
 *
 * @param name the name configuration gave the bridge, and the name a request selects it
 * by.
 * @param baseUrl the configured base URL.
 * @param urlProbed the catalog URL that was actually contacted.
 * @param reachable whether the catalog call answered with a JSON array.
 * @param reason the failure, only when unreachable.
 * @param actions the actions the sidecar advertised, empty when unreachable or when it
 * advertised none.
 * @author Ivan Rodriguez
 */
public record BridgeInfo(String name, String baseUrl, String urlProbed, boolean reachable, String reason,
		List<ActionInfo> actions) {
}
