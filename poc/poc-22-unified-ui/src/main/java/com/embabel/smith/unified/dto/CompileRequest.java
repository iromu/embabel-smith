/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.unified.dto;

/**
 * A natural-language spec description to compile into a draft, plus an optional agent
 * name.
 *
 * @param description The natural-language spec description.
 * @param agentName An optional agent name, or null to use the default.
 * @author Ivan Rodriguez
 */
public record CompileRequest(String description, String agentName) {

	public String agentNameOrDefault() {
		return (this.agentName == null || this.agentName.isBlank()) ? "draft-agent" : this.agentName;
	}
}
