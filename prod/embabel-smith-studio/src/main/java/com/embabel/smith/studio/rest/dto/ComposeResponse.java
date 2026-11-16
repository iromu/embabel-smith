/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
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
