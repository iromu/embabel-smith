/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest.dto;

/**
 * The result of rejecting a pending candidate (discarded, no publish).
 *
 * @param ok whether the candidate was rejected.
 * @param error the structured error (null on success).
 * @author Ivan Rodriguez
 */
public record RejectResponse(boolean ok, String error) {
}
