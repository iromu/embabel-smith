/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
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
