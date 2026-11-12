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
 * A pack in a source with its lifecycle state and drift status (the Lifecycle pane): the
 * {@code lifecycleState}/{@code baselinePassRate}/{@code updatedAt} are {@code null} when
 * the pack has no lifecycle sidecar; {@code drift} is true when the pack's content hash
 * has moved from the baseline recorded in the sidecar.
 *
 * @param name the pack name.
 * @param contentHash the pack's content hash.
 * @param lifecycleState the lifecycle state name (null without a sidecar).
 * @param baselinePassRate the baseline eval pass rate (null without a sidecar).
 * @param drift whether the content hash moved from the baseline.
 * @param updatedAt the last update time (null without a sidecar).
 * @author Ivan Rodriguez
 */
public record PackInfo(String name, String contentHash, String lifecycleState, Double baselinePassRate, boolean drift,
		String updatedAt) {
}
