/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.outerloop;

import java.util.HashSet;
import java.util.Set;

import com.embabel.smith.builder.eval.AgentSpecInvoker;
import com.embabel.smith.builder.eval.InvokerFactory;
import com.embabel.smith.common.model.AgentSpec;

/**
 * The distinctness guard (task 6.4): an {@link InvokerFactory} that stops a candidate
 * which answers two different task prompts with the same string.
 *
 * <p>
 * <b>Why the answers and not the prompts.</b> {@code builder/eval/AgentSpecInvoker} is a
 * single {@code String invoke(String input)} and exposes no prompt to inspect, so the
 * answers are the only evidence available that a candidate was actually asked the
 * question in front of it. POC 27's outer run spent its budget on exactly the case this
 * catches: no step prompt interpolated the task's input, so six eval tasks were one
 * request, every candidate saw one input, and no score the harness produced could have
 * distinguished two candidates. {@code AgentSpecValidator} has since closed the static
 * half of that ({@code PROMPT_IGNORES_DECLARED_INPUT}); this is the runtime half, and it
 * is the half that holds whatever the candidate's prompt is built from.
 *
 * <p>
 * <b>It is an early stop, not a post-mortem.</b> The check runs after each answer and
 * trips as soon as two distinct prompts have come back with one answer between them, so
 * the run stops after two calls rather than after the sixty it declared. Once a candidate
 * has given two different answers the guard can no longer trip for that candidate, which
 * is deliberate: the claim it defends is "this candidate was asked the questions", and
 * the first two answers are enough to have refuted it.
 *
 * <p>
 * The state is per deployed invoker, which is per candidate, because that is the unit the
 * claim is about. {@code Referee} records a candidate's answers per pack, so the gate's
 * re-eval replays them and reaches nothing here.
 *
 * @author Ivan Rodriguez
 */
public final class DistinctAnswerGuard implements InvokerFactory {

	/** Characters of the repeated answer quoted in the refusal. */
	private static final int ANSWER_QUOTE_CHARS = 120;

	private final InvokerFactory delegate;

	/**
	 * Wraps a factory so every deployed candidate is watched for a flat answer set.
	 * @param delegate the factory that actually deploys and runs the candidate
	 */
	public DistinctAnswerGuard(InvokerFactory delegate) {
		this.delegate = delegate;
	}

	/**
	 * {@inheritDoc}
	 * @param pack the pack to deploy
	 * @return an invoker that refuses a candidate answering different prompts alike.
	 */
	@Override
	public AgentSpecInvoker invokerFor(AgentSpec pack) {
		return new Watching(this.delegate.invokerFor(pack));
	}

	/**
	 * {@inheritDoc}
	 * @param pack the pack to deploy
	 * @param model the LLM the pack's steps should run on
	 * @return an invoker that refuses a candidate answering different prompts alike.
	 */
	@Override
	public AgentSpecInvoker invokerFor(AgentSpec pack, String model) {
		return new Watching(this.delegate.invokerFor(pack, model));
	}

	/**
	 * The watched half: one candidate's invoker, remembering how many different questions
	 * it was asked and how many different answers it gave.
	 *
	 * @author Ivan Rodriguez
	 */
	private static final class Watching implements AgentSpecInvoker {

		private final AgentSpecInvoker fresh;

		private final Set<String> prompts = new HashSet<>();

		private final Set<String> answers = new HashSet<>();

		private int asked;

		private String first;

		Watching(AgentSpecInvoker fresh) {
			this.fresh = fresh;
		}

		/**
		 * Ask the candidate, then check whether asking it anything at all is still worth
		 * paying for.
		 * @param input the task prompt
		 * @return the candidate's answer.
		 */
		@Override
		public String invoke(String input) {
			String answer = this.fresh.invoke(input);
			this.asked++;
			if (this.first == null) {
				this.first = answer;
			}
			this.prompts.add(input);
			this.answers.add(answer);
			if (this.prompts.size() > 1 && this.answers.size() == 1) {
				throw new OuterLoopRefused("the candidate gave one answer to " + this.prompts.size()
						+ " different task prompts: all " + this.asked + " answers so far are the same string, the"
						+ " first of them being \"" + abbreviate(this.first) + "\". A candidate that answers two"
						+ " different questions identically was not asked the questions, so the rest of the declared"
						+ " budget would buy the same evidence again.");
			}
			return answer;
		}

		private static String abbreviate(String answer) {
			if (answer == null) {
				return "(no answer — the goal produced no output)";
			}
			return (answer.length() <= ANSWER_QUOTE_CHARS) ? answer : answer.substring(0, ANSWER_QUOTE_CHARS) + "…";
		}

	}

}
