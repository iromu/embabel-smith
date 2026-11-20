/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.baseline;

import java.util.ArrayList;
import java.util.List;

import com.embabel.smith.builder.core.SpecPromptCaller;
import com.embabel.smith.selfimprove.innerloop.ModelCallMeter;
import com.embabel.smith.selfimprove.referee.FrozenTaskSet;

/**
 * Asks the frozen prompts of a live model and scores what comes back. This is the seam
 * the baseline and the noise floor are measured through, and it is the only place in this
 * package that reaches a model.
 *
 * <p>
 * <b>"Unmodified" is carried by the call itself.</b> Every prompt is sent with
 * {@code null} as its system message, because the seam only attaches a system prompt when
 * one is supplied. There is no improvement prompt, no candidate pack, and no self-
 * description in front of these questions — which is what task 2.4 means by the
 * unmodified process, and what makes this a measurement of the model's own priors rather
 * than of a generation. The frozen prompts are sent verbatim, including their backticks.
 *
 * <p>
 * Each prompt is asked once per {@link #measure()}, and the count is read off
 * {@link ModelCallMeter} rather than asserted from the code, so a run's cost is a number
 * the evidence carries. Feeding this class a canned {@link SpecPromptCaller} is how the
 * offline tests drive it without a model.
 *
 * @author Ivan Rodriguez
 */
public final class BaselineRun {

	private final FrozenTaskSet tasks;

	private final SpecPromptCaller answers;

	private final ModelCallMeter meter;

	/**
	 * Create a measurement run.
	 * @param tasks the frozen set to ask
	 * @param answers the answer seam; the live run passes
	 * {@link com.embabel.smith.builder.core.AiSpecPromptCaller}, an offline test passes a
	 * canned table
	 * @param meter the counter every paid call has to pass through
	 */
	public BaselineRun(FrozenTaskSet tasks, SpecPromptCaller answers, ModelCallMeter meter) {
		this.tasks = tasks;
		this.answers = answers;
		this.meter = meter;
	}

	/**
	 * One pass: ask every frozen prompt once, with nothing in front of it, and score the
	 * answers on both arms.
	 * @return both arms of the score for this pass
	 */
	public ScoredAnswers measure() {
		return ScoredAnswers.of(this.tasks, askOnce());
	}

	/**
	 * Several passes over the same unchanged process, for the repeat-to-repeat floor.
	 * @param repeats how many passes to make
	 * @return one scoring per pass, in pass order
	 */
	public List<ScoredAnswers> repeats(int repeats) {
		List<ScoredAnswers> passes = new ArrayList<>();
		for (int pass = 0; pass < repeats; pass++) {
			passes.add(measure());
		}
		return List.copyOf(passes);
	}

	/**
	 * The calls spent so far, read off the meter.
	 * @return the model calls this run has made
	 */
	public int modelCalls() {
		return this.meter.count();
	}

	private List<String> askOnce() {
		List<String> answers = new ArrayList<>();
		for (var task : this.tasks.tasks()) {
			answers.add(this.meter.counted(() -> this.answers.call(null, task.prompt())));
		}
		return List.copyOf(answers);
	}

}
