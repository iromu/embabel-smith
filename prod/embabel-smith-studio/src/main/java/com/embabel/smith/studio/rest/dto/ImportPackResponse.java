/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest.dto;

/**
 * The import-a-pack response (change: smith-studio-postgres-storage): the stored pack
 * name on success, or a structured error when the path is not a valid agent-spec
 * directory (nothing is stored in that case).
 *
 * @param ok whether the pack was imported.
 * @param name the stored pack name on success.
 * @param error the structured error (null on success).
 * @author Ivan Rodriguez
 */
public record ImportPackResponse(boolean ok, String name, String error) {

	public static ImportPackResponse of(String name) {
		return new ImportPackResponse(true, name, null);
	}

	public static ImportPackResponse failed(String message) {
		return new ImportPackResponse(false, null, message);
	}
}
