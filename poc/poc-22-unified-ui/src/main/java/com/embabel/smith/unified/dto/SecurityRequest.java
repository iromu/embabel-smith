/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.unified.dto;

/**
 * A request to scan one agent spec at install time.
 *
 * @param specName The name of the spec to scan.
 * @author Ivan Rodriguez
 */
public record SecurityRequest(String specName) {
}
