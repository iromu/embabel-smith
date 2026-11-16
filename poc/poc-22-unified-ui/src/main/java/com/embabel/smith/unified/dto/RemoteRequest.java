/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.unified.dto;

/**
 * A request to invoke the remote action on the backing sidecar.
 *
 * @param input The input for the remote action, or null to use an empty input.
 * @author Ivan Rodriguez
 */
public record RemoteRequest(String input) {

	public String inputOrDefault() {
		return (this.input != null) ? this.input : "";
	}
}
