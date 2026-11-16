/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.unified.dto;

/**
 * A request to run a agent spec over an input (the no-code runtime, POC 1).
 *
 * @param specName The name of the spec to run.
 * @param input The input to run the spec over, or null to use an empty input.
 * @author Ivan Rodriguez
 */
public record RunRequest(String specName, String input) {

	public String inputOrDefault() {
		return (this.input != null) ? this.input : "";
	}
}
