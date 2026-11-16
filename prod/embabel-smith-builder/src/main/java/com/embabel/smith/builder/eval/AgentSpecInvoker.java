/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.eval;

/**
 * Runs the built spec once and returns the goal output's content. The production
 * implementation deploys the pack and invokes it as a single typed goal run
 * (endpoint-gated); offline tests inject a fake so the evaluate → score → refine flow is
 * verifiable without a live model.
 *
 * @author Ivan Rodriguez
 */
@FunctionalInterface
public interface AgentSpecInvoker {

	String invoke(String input);

}
