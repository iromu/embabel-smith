/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest.dto;

import com.embabel.smith.common.model.AgentSpec;

/**
 * The Compose pane's export request (change 07): an assembled agent spec — a goal plus
 * its action steps wired by type names — and the source to publish it to. The pack is
 * fully specified by the client (the flow was already authored in the graph editor); the
 * controller only validates its shape and writes it.
 *
 * @param source the source to publish the pack to.
 * @param pack the assembled agent spec to write.
 * @param overwrite whether to overwrite an existing pack.
 * @param push whether to push the pack to a remote.
 * @author Ivan Rodriguez
 */
public record ComposeRequest(String source, AgentSpec pack, Boolean overwrite, Boolean push) {
}
