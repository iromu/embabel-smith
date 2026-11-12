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

import java.util.Map;

/**
 * The discover response: the loaded business-process documents (filename to content) and
 * where they came from. The workspace presents these and then generates from the same
 * location.
 *
 * @param sourceType where the docs came from (folder or git).
 * @param location the docs location.
 * @param docs the loaded business-process documents (filename to content).
 * @param error the structured error (null on success).
 * @author Ivan Rodriguez
 */
public record DiscoverResponse(String sourceType, String location, Map<String, String> docs, String error) {

	public static DiscoverResponse of(String sourceType, String location, Map<String, String> docs) {
		return new DiscoverResponse(sourceType, location, docs, null);
	}

	public static DiscoverResponse failed(String message) {
		return new DiscoverResponse(null, null, null, message);
	}
}
