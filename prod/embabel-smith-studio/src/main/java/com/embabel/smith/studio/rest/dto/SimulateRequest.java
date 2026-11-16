/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
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
 * @param model the LLM every step of the dry-run uses; blank uses the model each step's
 * own spec names.
 * @author Ivan Rodriguez
 */
public record SimulateRequest(AgentSpec pack, String input, String model) {
}
