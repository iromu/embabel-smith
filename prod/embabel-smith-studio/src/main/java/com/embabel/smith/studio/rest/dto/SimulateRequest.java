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

package com.embabel.smith.studio.rest.dto;

import com.embabel.smith.common.model.AgentSpec;

/**
 * The simulate request: the generated (or edited) {@link AgentSpec} to dry-run, and the
 * sample {@code input} bound to its {@code UserInput}. The pack is run in a throwaway
 * platform; nothing is published.
 *
 * @param pack the generated (or edited) agent spec to dry-run.
 * @param input the sample input bound to the pack's {@code UserInput}.
 * @author Ivan Rodriguez
 */
public record SimulateRequest(AgentSpec pack, String input) {
}
