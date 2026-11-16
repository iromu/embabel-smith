/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest.dto;

/**
 * The body of an on-demand loop iteration. It is optional in full — the button can be
 * pressed with no body at all — and names nothing but the LLM to iterate on.
 *
 * @param model the LLM this iteration generates, evaluates and refines with; blank uses
 * the configured default.
 * @author Ivan Rodriguez
 */
public record LoopTriggerRequest(String model) {
}
