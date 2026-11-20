/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
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
		return new PublishInfo(result.location(), result.pushed());
	}
}
