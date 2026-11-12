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

package com.embabel.smith.studio.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Persists the storage settings as a local JSON document (change:
 * smith-studio-postgres-storage).
 *
 * <p>
 * The settings configure the database, so they cannot live in it — they are written to
 * {@code
 *
<dir>
 * /storage.json} (default {@code ~/.smith/studio}), mode 0600 so the password is not
 * world-readable. The directory is overridable (a property in production, a temp dir in
 * tests). On a non-POSIX filesystem the permission is set best-effort and ignored.
 *
 * @author Ivan Rodriguez
 */
public class StorageSettingsService {

	private static final String FILE_NAME = "storage.json";

	private final Path file;

	private final ObjectMapper json = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL)
		.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

	public StorageSettingsService(Path directory) {
		this.file = directory.resolve(FILE_NAME);
	}

	/**
	 * The settings document, or {@code null} when no document exists yet.
	 * @return the settings document, or {@code null} when no document exists yet.
	 */
	public StorageSettings load() {
		if (!Files.isRegularFile(this.file)) {
			return null;
		}
		try {
			return this.json.readValue(Files.readString(this.file), StorageSettings.class);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to read storage settings from " + this.file + ": " + ex.getMessage(),
					ex);
		}
	}

	/**
	 * Writes the settings document, creating the parent directory and setting mode 0600.
	 * @param settings the settings to persist.
	 */
	public void save(StorageSettings settings) {
		try {
			Files.createDirectories(this.file.getParent());
			Files.writeString(this.file, this.json.writeValueAsString(settings));
			setOwnerOnlyPermissions(this.file);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to save storage settings to " + this.file, ex);
		}
	}

	private void setOwnerOnlyPermissions(Path path) {
		try {
			Set<PosixFilePermission> perms = EnumSet.of(PosixFilePermission.OWNER_READ,
					PosixFilePermission.OWNER_WRITE);
			Files.setPosixFilePermissions(path, perms);
		}
		catch (UnsupportedOperationException ignored) {
			// Non-POSIX filesystem: the owner-only mode cannot be applied.
		}
		catch (IOException ignored) {
			// Best-effort: the document is still written.
		}
	}

}
