/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
/**
 * The agent spec manifest — declared in {@code manifest.yml}.
 *
 * <p>
 * Requires a name and a version; may declare a default planner (e.g. {@code GOAP}) and a
 * default model. The runtime honours these as per-pack defaults that a run may override.
 * @param name the name of the agent spec
 * @param version the version of the agent spec
 * @param defaultPlanner the default planner to use, or {@code null}
 * @param defaultModel the default model to use, or {@code null}
 * @author Ivan Rodriguez
 */

public record AgentSpecManifest(@JsonProperty("name") String name, @JsonProperty("version") String version,
		@JsonProperty("defaultPlanner") String defaultPlanner, @JsonProperty("defaultModel") String defaultModel) {

	public AgentSpecManifest(String name, String version) {
		this(name, version, null, null);
	}
}
