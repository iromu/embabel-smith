/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.unified.dto;

/**
 * The result of a run (POC 1): the goal output, or a clear error. {@link #error} is
 * non-null when the run could not happen (no spec selected, an unknown spec, no endpoint
 * configured, or the plan failed) — in which case {@link #output} is null.
 *
 * @param error The error when the run could not happen, or null.
 * @param output The goal output, or null when the run failed.
 * @author Ivan Rodriguez
 */
public record RunResponse(String error, String output) {

	public static RunResponse ok(String output) {
		return new RunResponse(null, output);
	}

	public static RunResponse error(String error) {
		return new RunResponse(error, null);
	}

	public static RunResponse noEndpoint(String error) {
		return new RunResponse(error, null);
	}
}
