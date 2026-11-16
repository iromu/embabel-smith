/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
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
