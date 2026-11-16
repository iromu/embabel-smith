/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest.dto;

/**
 * The save-a-pack response: whether the pack was stored, the name it was saved under, and
 * a structured error.
 *
 * @param ok whether the pack was stored.
 * @param name the name the pack was saved under.
 * @param error the structured error (null on success).
 * @author Ivan Rodriguez
 */
public record SavePackResponse(boolean ok, String name, String error) {

	public static SavePackResponse of(String name) {
		return new SavePackResponse(true, name, null);
	}

	public static SavePackResponse failed(String message) {
		return new SavePackResponse(false, null, message);
	}
}
