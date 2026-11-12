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

package com.embabel.smith.studio.rest;

import com.embabel.smith.studio.rest.dto.StorageSettingsRequest;
import com.embabel.smith.studio.rest.dto.StorageSettingsResponse;
import com.embabel.smith.studio.storage.StorageContext;
import com.embabel.smith.studio.storage.StorageSettings;
import com.embabel.smith.studio.storage.StorageSettingsService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The storage settings surface (change: smith-studio-postgres-storage). {@code GET}
 * returns the current settings (the persisted document, or the defaults when none);
 * {@code PUT} validates the endpoint, rebinds storage live on success, persists the
 * settings, and keeps the previous binding in effect when the endpoint is unreachable.
 *
 * @author Ivan Rodriguez
 */
@RestController
public class StorageSettingsController {

	private final StorageContext storageContext;

	private final StorageSettingsService settingsService;

	public StorageSettingsController(StorageContext storageContext, StorageSettingsService settingsService) {
		this.storageContext = storageContext;
		this.settingsService = settingsService;
	}

	@GetMapping("/api/storage-settings")
	public StorageSettings getSettings() {
		StorageSettings loaded = this.settingsService.load();
		return (loaded != null) ? loaded : StorageSettings.defaults();
	}

	@PutMapping("/api/storage-settings")
	public StorageSettingsResponse setSettings(@RequestBody StorageSettingsRequest request) {
		StorageSettings settings = toSettings(request);
		try {
			this.storageContext.bind(settings);
		}
		catch (RuntimeException ex) {
			return StorageSettingsResponse.failed((ex.getMessage() == null) ? ex.toString() : ex.getMessage());
		}
		this.settingsService.save(settings);
		return StorageSettingsResponse.of(settings);
	}

	/**
	 * Maps the flat Storage-pane form onto the settings document; the trace endpoint is
	 * kept only for the separate-database backend.
	 * @param request the Storage-pane form to map.
	 * @return the mapped settings document.
	 */
	private static StorageSettings toSettings(StorageSettingsRequest request) {
		boolean separate = StorageSettings.TRACE_POSTGRES_SEPARATE.equals(request.traceType());
		return new StorageSettings(new StorageSettings.DbSettings(request.url(), request.user(), request.password()),
				new StorageSettings.TraceSettings(request.traceType(), (separate) ? request.traceUrl() : null,
						(separate) ? request.traceUser() : null, (separate) ? request.tracePassword() : null));
	}

}
