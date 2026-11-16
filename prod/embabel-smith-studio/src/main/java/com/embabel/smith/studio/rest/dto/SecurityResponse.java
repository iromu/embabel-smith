/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest.dto;

/**
 * The result of scanning a agent spec (the Security pane): the scan report, or the error.
 *
 * @param error the structured error (null on success).
 * @param scan the scan report (null on failure).
 * @author Ivan Rodriguez
 */
public record SecurityResponse(String error, ScanInfo scan) {

	public static SecurityResponse ok(ScanInfo scan) {
		return new SecurityResponse(null, scan);
	}

	public static SecurityResponse error(String error) {
		return new SecurityResponse(error, null);
	}
}
