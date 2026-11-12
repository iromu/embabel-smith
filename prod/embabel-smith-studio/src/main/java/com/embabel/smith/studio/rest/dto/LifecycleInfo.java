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

import com.embabel.smith.builder.lifecycle.LifecycleMetadata;

/**
 * The lifecycle state of a agent spec (mirrors {@code LifecycleMetadata}).
 *
 * @param contentHash the pack's content hash.
 * @param baselinePassRate the baseline eval pass rate.
 * @param state the lifecycle state name.
 * @param updatedAt the last update time (ISO-8601).
 * @author Ivan Rodriguez
 */
public record LifecycleInfo(String contentHash, double baselinePassRate, String state, String updatedAt) {

	public static LifecycleInfo of(LifecycleMetadata metadata) {
		return new LifecycleInfo(metadata.contentHash(), metadata.baselinePassRate(), metadata.state().name(),
				metadata.updatedAt());
	}
}
