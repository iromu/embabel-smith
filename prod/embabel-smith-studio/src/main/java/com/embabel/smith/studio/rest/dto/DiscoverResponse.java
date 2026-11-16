/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
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
