/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest.dto;

/**
 * The loop's schedule + auto-publish status (the Auto-improve pane).
 *
 * @param running whether the loop is running.
 * @param autoPublish whether auto-publish mode is on.
 * @author Ivan Rodriguez
 */
public record LoopStatus(boolean running, boolean autoPublish) {
}
