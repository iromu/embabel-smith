/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest.dto;

/**
 * A single security finding (mirrors {@code com.embabel.smith.builder.security.Finding}).
 *
 * @param severity the finding severity.
 * @param category the finding category.
 * @param message the finding message.
 * @param evidence the supporting evidence.
 * @author Ivan Rodriguez
 */
public record SecurityFinding(String severity, String category, String message, String evidence) {
}
