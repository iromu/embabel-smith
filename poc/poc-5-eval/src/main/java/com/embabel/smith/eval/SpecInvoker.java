/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.eval;

/**
 * Abstracts "run the goal-type spec on an input and return the goal output's content."
 * The endpoint-gated production implementation is {@link AgentInvocationSpecInvoker} (via
 * {@code AgentInvocation}); offline tests supply a fake so the eval/scoring path is
 * verifiable without a live model.
 *
 * @author Ivan Rodriguez
 */
@FunctionalInterface
public interface SpecInvoker {

	String invoke(String input);

}
