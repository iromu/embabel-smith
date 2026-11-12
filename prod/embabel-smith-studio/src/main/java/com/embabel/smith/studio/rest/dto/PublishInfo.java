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

import com.embabel.smith.builder.publish.PublishResult;

/**
 * Where a pack was published (mirrors {@code PublishResult}).
 *
 * @param location where the pack was published.
 * @param pushed whether it was pushed to a remote.
 * @author Ivan Rodriguez
 */
public record PublishInfo(String location, boolean pushed) {

	public static PublishInfo of(PublishResult result) {
		return new PublishInfo(result.location().toString(), result.pushed());
	}
}
