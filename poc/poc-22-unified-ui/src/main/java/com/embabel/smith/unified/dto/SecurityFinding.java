/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.unified.dto;

/**
 * One security finding: severity, risk category, a human message, and the concrete
 * evidence.
 *
 * @param severity The finding severity.
 * @param category The risk category.
 * @param message A human-readable message.
 * @param evidence The concrete evidence.
 * @author Ivan Rodriguez
 */
public record SecurityFinding(String severity, String category, String message, String evidence) {
}
