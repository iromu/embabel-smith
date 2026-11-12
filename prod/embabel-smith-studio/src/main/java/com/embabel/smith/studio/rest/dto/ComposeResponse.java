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
 * The Compose pane's export result: where the pack landed, the goal it exposes, and its
 * lifecycle stamp. A fresh composed pack is stamped {@code INCUBATING} with a 0.0
 * baseline (not yet eval'd); the improvement loop establishes the real baseline later.
 *
 * @param ok whether the pack was written.
 * @param packName the name the pack was saved under.
 * @param goal the goal the pack exposes.
 * @param published where the pack landed (null when not published).
 * @param lifecycle the lifecycle stamp of the pack.
 * @param error the structured error (null on success).
 * @author Ivan Rodriguez
 */
public record ComposeResponse(boolean ok, String packName, String goal, PublishInfo published, LifecycleInfo lifecycle,
		String error) {

	public static ComposeResponse published(String packName, String goal, PublishInfo published,
			LifecycleInfo lifecycle) {
		return new ComposeResponse(true, packName, goal, published, lifecycle, null);
	}

	public static ComposeResponse failed(String error) {
		return new ComposeResponse(false, null, null, null, null, error);
	}
}
