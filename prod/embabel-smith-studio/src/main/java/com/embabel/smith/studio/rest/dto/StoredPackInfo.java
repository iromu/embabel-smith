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
 * A saved pack's listing entry: its name, the goal it exports, its step count, and when
 * it was saved (ISO-8601).
 *
 * @param name the saved pack name.
 * @param goal the goal the pack exports.
 * @param steps the step count.
 * @param savedAt when the pack was saved (ISO-8601).
 * @author Ivan Rodriguez
 */
public record StoredPackInfo(String name, String goal, int steps, String savedAt) {
}
