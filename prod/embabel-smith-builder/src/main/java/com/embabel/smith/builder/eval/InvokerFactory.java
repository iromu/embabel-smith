/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.eval;

import com.embabel.smith.common.model.AgentSpec;

/**
 * Produces the {@link AgentSpecInvoker} for a given pack. The production implementation
 * deploys the pack and returns an invoker that runs it; because the pack's prompts change
 * across refinement rounds, a fresh invoker is built for each round's pack.
 *
 * @author Ivan Rodriguez
 */
@FunctionalInterface
public interface InvokerFactory {

	AgentSpecInvoker invokerFor(AgentSpec pack);

	/**
	 * As {@link #invokerFor(AgentSpec)}, running every action step through one particular
	 * LLM. The default ignores the request — a fake invoker answers for any model — while
	 * the production factory pins it onto the steps it deploys.
	 * @param pack the pack to deploy
	 * @param model the LLM the pack's steps should run on, or empty for the configured
	 * default
	 * @return the invoker for this pack
	 */
	default AgentSpecInvoker invokerFor(AgentSpec pack, String model) {
		return invokerFor(pack);
	}

}
