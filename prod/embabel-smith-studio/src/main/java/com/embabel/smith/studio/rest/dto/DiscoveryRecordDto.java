/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
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
