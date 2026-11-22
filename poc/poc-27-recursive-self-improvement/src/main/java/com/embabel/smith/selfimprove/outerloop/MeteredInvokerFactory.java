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
 * <p>
 * <b>One counted call is not one model call.</b> Task 4.1 made the candidate a builder
 * pack rather than the single step this loop used to assemble here, so one invocation
 * counted now drives every step of that pack — five on {@code builder-single-spec}.
 * Nothing below this seam can report them: {@code AgentSpecInvoker} is one
 * {@code invoke(String)} with no per-step hook, so the number this counter holds is
 * invocations per frozen prompt, which is what {@code Budget.costPerCandidate} was
 * derived against. What a live pass actually spends behind that number is the candidate's
 * step count times it, and that gap is the budget's to carry, not something to fix by
 * inflating a counter that is also the run's evidence.
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
