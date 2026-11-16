/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.eval;

import com.embabel.smith.common.model.ProcessSpec;

/**
 * Produces the {@link AgentSpecInvoker} for a composed process. The production
 * implementation deploys <em>every</em> pack of the process into one scope — so the
 * planner can chain across the hand-off types — and returns an invoker that runs the
 * process's entry goal, the same way the runtime composes a multi-pack source. It is the
 * process counterpart of {@link InvokerFactory} and is implemented by the same class.
 *
 * @author Ivan Rodriguez
 */
@FunctionalInterface
public interface ProcessInvokerFactory {

	/**
	 * The invoker that runs a process end to end.
	 * @param process the process to deploy and run
	 * @return an invoker that runs the process's entry goal against its packs
	 */
	AgentSpecInvoker invokerFor(ProcessSpec process);

	/**
	 * As {@link #invokerFor(ProcessSpec)}, running every step of every pack through one
	 * particular LLM.
	 * @param process the process to deploy and run
	 * @param model the LLM the process's steps should run on, or empty for the configured
	 * default
	 * @return an invoker that runs the process's entry goal against its packs
	 */
	default AgentSpecInvoker invokerFor(ProcessSpec process, String model) {
		return invokerFor(process);
	}

}
