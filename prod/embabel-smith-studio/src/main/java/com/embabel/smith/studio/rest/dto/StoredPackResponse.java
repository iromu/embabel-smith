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
