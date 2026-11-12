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
 * The Compose pane's export request (change 07): an assembled agent spec — a goal plus
 * its action steps wired by type names — and the source to publish it to. The pack is
 * fully specified by the client (the flow was already authored in the graph editor); the
 * controller only validates its shape and writes it.
 *
 * @param source the source to publish the pack to.
 * @param pack the assembled agent spec to write.
 * @param overwrite whether to overwrite an existing pack.
 * @param push whether to push the pack to a remote.
 * @author Ivan Rodriguez
 */
public record ComposeRequest(String source, AgentSpec pack, Boolean overwrite, Boolean push) {
}
