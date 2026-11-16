/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest.dto;

/**
 * The result of approving a pending candidate: where it was published, or the error.
 *
 * @param ok whether the candidate was approved and published.
 * @param location where the candidate was published.
 * @param error the structured error (null on success).
 * @author Ivan Rodriguez
 */
public record ApproveResponse(boolean ok, String location, String error) {
}
