/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
 *
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
