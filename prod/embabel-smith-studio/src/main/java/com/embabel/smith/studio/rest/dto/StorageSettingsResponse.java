/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
