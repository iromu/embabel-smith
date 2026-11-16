/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
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
 * @param model the LLM the whole build runs on — generation, eval and refinement
 * (optional; blank uses the configured default).
 * @author Ivan Rodriguez
 */
public record BuildRequest(String description, String outputSource, Integer maxRefine, Boolean overwrite, Boolean push,
		String model) {
}
