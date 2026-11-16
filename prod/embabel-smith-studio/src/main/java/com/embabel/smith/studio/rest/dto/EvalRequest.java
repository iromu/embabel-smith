/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest.dto;

import java.util.List;

/**
 * An eval request (the Eval pane): the source and spec to evaluate, and an optional task
 * set (a default task is used when omitted). Endpoint-gated — the evaluation runs the
 * spec.
 *
 * @param source the spec source to evaluate.
 * @param spec the spec name to evaluate.
 * @param tasks the eval task set (a default task is used when omitted).
 * @param model the LLM the eval and any refinement run on; blank uses the configured
 * default.
 * @author Ivan Rodriguez
 */
public record EvalRequest(String source, String spec, List<EvalTaskDto> tasks, String model) {
}
