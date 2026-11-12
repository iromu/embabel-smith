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

package com.embabel.smith.builder.core;

import java.util.List;

import com.embabel.smith.common.model.AgentSpec;

/**
 * The output of the {@code compileFromDescription} step: a draft spec (a
 * {@link AgentSpec}) produced by the natural-language compiler, together with any issues
 * the compiler surfaced for review. A draft with issues is still usable but must be
 * reviewed before it is trusted.
 *
 * @param agentName The name derived from the description.
 * @param pack The compiled draft agent spec.
 * @param issues The issues the compiler surfaced for review.
 * @author Ivan Rodriguez
 */
public record CompiledDraft(String agentName, AgentSpec pack, List<String> issues) {

	public CompiledDraft {
		issues = (issues != null) ? List.copyOf(issues) : List.of();
	}

	public boolean hasIssues() {
		return !this.issues.isEmpty();
	}
}
