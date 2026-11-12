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
