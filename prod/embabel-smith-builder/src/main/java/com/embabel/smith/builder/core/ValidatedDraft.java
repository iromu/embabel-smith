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
 * The output of the {@code validateDraft} step: the draft plus whether its bindings and
 * schemas are consistent. When {@code valid} is false, the draft MUST NOT be written
 * (design D5) — the builder reports the issues instead.
 *
 * @param pack The validated draft agent spec.
 * @param valid Whether the draft's bindings and schemas are consistent.
 * @param issues The validation issues, if any.
 * @author Ivan Rodriguez
 */
public record ValidatedDraft(AgentSpec pack, boolean valid, List<String> issues) {

	public ValidatedDraft {
		issues = (issues != null) ? List.copyOf(issues) : List.of();
	}
}
