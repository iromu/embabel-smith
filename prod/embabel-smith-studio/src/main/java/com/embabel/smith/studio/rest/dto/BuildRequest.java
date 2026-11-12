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

/**
 * A build request (the Author pane): a natural-language description of the spec, the
 * source URI to publish to, and optional build knobs (refinement bound, overwrite, push).
 *
 * @param description the natural-language description of the spec to build.
 * @param outputSource the source URI to publish the pack to.
 * @param maxRefine the refinement iteration bound (optional).
 * @param overwrite whether to overwrite an existing pack.
 * @param push whether to push the pack to a remote.
 * @author Ivan Rodriguez
 */
public record BuildRequest(String description, String outputSource, Integer maxRefine, Boolean overwrite,
		Boolean push) {
}
