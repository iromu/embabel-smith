/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest.dto;

/**
 * Sets the auto-publish mode (the Auto-improve pane's toggle).
 *
 * @param autoPublish whether auto-publish mode is on.
 * @author Ivan Rodriguez
 */
public record AutoPublishRequest(boolean autoPublish) {
}
