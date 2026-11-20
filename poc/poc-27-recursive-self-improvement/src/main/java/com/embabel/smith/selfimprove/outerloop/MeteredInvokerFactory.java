/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.outerloop;

import com.embabel.smith.builder.eval.AgentSpecInvoker;
import com.embabel.smith.builder.eval.InvokerFactory;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.selfimprove.innerloop.ModelCallMeter;

/**
 * An {@link InvokerFactory} that charges every model call to a {@link ModelCallMeter} as
 * it happens.
 *
 * <p>
 * The outer loop's cost is its result: a run that cannot say what it spent cannot say
 * what it bought, and a budget it cannot observe is not a limit. Counting is therefore
 * done at the only place every call must pass through — the invoker — so the number the
 * run reports is read off a counter rather than written down next to a per-variant
 * constant someone believed.
 *
 * <p>
 * Hand the wrapped factory to {@code Referee.start} and the cost is still one call per
 * frozen prompt per candidate: the referee records a candidate's answers per pack, so the
 * gate's re-eval replays them instead of asking again, and a replay never reaches this
 * class.
 *
 * @author Ivan Rodriguez
 */
public final class MeteredInvokerFactory implements InvokerFactory {

	private final InvokerFactory delegate;

	private final ModelCallMeter meter;

	/**
	 * Wraps a factory so its calls are counted.
	 * @param delegate the factory that actually produces invokers
	 * @param meter the counter every call is charged to
	 */
	public MeteredInvokerFactory(InvokerFactory delegate, ModelCallMeter meter) {
		this.delegate = delegate;
		this.meter = meter;
	}

	/**
	 * {@inheritDoc}
	 * @param pack the pack to deploy
	 * @return an invoker charging every call to the meter.
	 */
	@Override
	public AgentSpecInvoker invokerFor(AgentSpec pack) {
		return metered(this.delegate.invokerFor(pack));
	}

	/**
	 * {@inheritDoc}
	 * @param pack the pack to deploy
	 * @param model the LLM the pack's steps should run on
	 * @return an invoker charging every call to the meter.
	 */
	@Override
	public AgentSpecInvoker invokerFor(AgentSpec pack, String model) {
		return metered(this.delegate.invokerFor(pack, model));
	}

	private AgentSpecInvoker metered(AgentSpecInvoker fresh) {
		return (input) -> this.meter.counted(() -> fresh.invoke(input));
	}

}
