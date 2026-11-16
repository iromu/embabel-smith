/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest.dto;

import com.embabel.smith.common.model.AgentSpec;

/**
 * The load-a-saved-pack response: the reloaded {@link AgentSpec} (serialisable to the
 * frontend's ComposePack shape) or a structured error when no such pack is stored.
 *
 * @param pack the reloaded agent spec (null on failure).
 * @param error the structured error (null on success).
 * @author Ivan Rodriguez
 */
public record StoredPackResponse(AgentSpec pack, String error) {

	public static StoredPackResponse of(AgentSpec pack) {
		return new StoredPackResponse(pack, null);
	}

	public static StoredPackResponse failed(String message) {
		return new StoredPackResponse(null, message);
	}
}
