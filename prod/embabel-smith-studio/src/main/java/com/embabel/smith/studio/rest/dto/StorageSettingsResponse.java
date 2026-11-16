/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest.dto;

import com.embabel.smith.studio.storage.StorageSettings;

/**
 * The storage-settings response (change: smith-studio-postgres-storage). On a successful
 * save the new settings are returned; on a failed save (unreachable endpoint)
 * {@code error} carries the connection failure and the previous working binding remains
 * in effect.
 *
 * @param ok whether the settings were saved.
 * @param settings the new settings (null on failure).
 * @param error the connection failure on a failed save.
 * @author Ivan Rodriguez
 */
public record StorageSettingsResponse(boolean ok, StorageSettings settings, String error) {

	public static StorageSettingsResponse of(StorageSettings settings) {
		return new StorageSettingsResponse(true, settings, null);
	}

	public static StorageSettingsResponse failed(String message) {
		return new StorageSettingsResponse(false, null, message);
	}
}
