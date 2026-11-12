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
 * A recorded discovery in the durable history: when it happened, where the docs came
 * from, and how many documents were loaded.
 *
 * @param timestamp when the discovery happened (ISO-8601).
 * @param sourceType where the docs came from (folder or git).
 * @param location the docs location.
 * @param docCount how many documents were loaded.
 * @author Ivan Rodriguez
 */
public record DiscoveryRecordDto(String timestamp, String sourceType, String location, int docCount) {
}
