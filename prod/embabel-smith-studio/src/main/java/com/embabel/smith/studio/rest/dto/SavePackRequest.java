/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest.dto;

import com.embabel.smith.common.model.AgentSpec;

/**
 * The save-a-pack request: the {@link AgentSpec} to persist to the durable store.
 *
 * @param pack the agent spec to persist.
 * @author Ivan Rodriguez
 */
public record SavePackRequest(AgentSpec pack) {
}
