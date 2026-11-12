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

package com.embabel.smith.ui.dto;

/**
 * A natural-language spec description to compile into a draft, plus an optional agent
 * name.
 *
 * @param description the natural-language spec description to compile
 * @param agentName the agent name to use, or null to use the default
 * @author Ivan Rodriguez
 */
public record CompileRequest(String description, String agentName) {

	public String agentNameOrDefault() {
		return (this.agentName == null || this.agentName.isBlank()) ? "draft-agent" : this.agentName;
	}
}
