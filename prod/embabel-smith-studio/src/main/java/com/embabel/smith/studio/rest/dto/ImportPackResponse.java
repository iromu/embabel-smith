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
