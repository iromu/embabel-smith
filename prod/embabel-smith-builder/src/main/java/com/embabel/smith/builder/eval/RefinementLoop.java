/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.embabel.smith.builder.eval;

import java.util.ArrayList;
import java.util.List;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.validation.AgentSpecValidator;

/**
 * The bounded eval → refine loop (design D1, extracted from POC 5 + POC 9). Each round it
 * runs the built pack against the eval tasks, and — while the score is below the gate and
 * the iteration bound has not been reached — rewrites the terminal action's prompt from
 * the failures, re-validates it, and tries again. It never corrupts the pack: a rewrite
 * that fails validation is discarded.
 *
 * @author Ivan Rodriguez
 */
public class RefinementLoop {

	private final InvokerFactory invokerFactory;

	private final PromptRewriter rewriter;

	private final List<EvalTask> tasks;

	private final double scoreGate;

	private final int maxRefine;

	private final AgentSpecValidator validator = new AgentSpecValidator();

	public RefinementLoop(InvokerFactory invokerFactory, PromptRewriter rewriter, List<EvalTask> tasks,
			double scoreGate, int maxRefine) {
		this.invokerFactory = invokerFactory;
		this.rewriter = rewriter;
		this.tasks = (tasks != null) ? List.copyOf(tasks) : List.of();
		this.scoreGate = scoreGate;
		this.maxRefine = Math.max(0, maxRefine);
	}

	public RefinementOutcome refine(AgentSpec initial) {
		AgentSpec current = initial;
		double score = 0.0;
		List<String> failures = List.of();
		int refinements = 0;
		for (int round = 0; round <= this.maxRefine; round++) {
			AgentSpecInvoker invoker = this.invokerFactory.invokerFor(current);
			List<String> outputs = new ArrayList<>();
			for (EvalTask task : this.tasks) {
				outputs.add(invoker.invoke(task.input()));
			}
			failures = EvalScorer.failingTasks(this.tasks, outputs);
			score = EvalScorer.score(this.tasks, outputs);
			if (score >= this.scoreGate) {
				break; // gate met
			}
			if (round == this.maxRefine) {
				break; // iteration bound reached
			}
			current = rewriteTerminalPrompt(current, failures);
			refinements++;
		}
		return new RefinementOutcome(current, score, refinements, failures, score >= this.scoreGate);
	}

	/**
	 * Rewrites the terminal action's prompt (the one producing the goal output) from the
	 * failures, re-validates the resulting pack, and returns it — or the original pack
	 * when the rewrite does not validate, so the loop never corrupts a good draft.
	 * @param pack the pack whose terminal action prompt is rewritten
	 * @param failures the names of the failing eval tasks
	 * @return the rewritten and validated pack, or the original pack when the rewrite
	 *         does not validate
	 */
	private AgentSpec rewriteTerminalPrompt(AgentSpec pack, List<String> failures) {
		ActionStepSpec terminal = terminalAction(pack);
		if (terminal == null) {
			return pack;
		}
		String newPrompt = this.rewriter
			.rewrite(terminal.prompt(), "Eval failures: " + describeFailures(failures));
		ActionStepSpec revised = new ActionStepSpec(terminal.stepType(), terminal.name(), terminal.description(),
				terminal.inputTypeNames(), terminal.outputTypeName(), newPrompt, terminal.pre(), terminal.post(),
				terminal.cost(), terminal.value(), terminal.canRerun(), terminal.nullable(), terminal.toolGroups());
		List<ActionStepSpec> steps = pack.steps()
			.stream()
			.map((s) -> (s.name().equals(terminal.name())) ? revised : s)
			.toList();
		AgentSpec candidate = new AgentSpec(pack.manifest(), pack.goal(), steps, pack.types());
		return this.validator.validate(candidate).isValid() ? candidate : pack;
	}

	/**
	 * Describes the failing tasks for the rewriter: one line per task with its input and
	 * expected output (POC 9), falling back to the bare name when the task is not found
	 * in the set.
	 * @param failingTaskNames the names of the failing eval tasks
	 * @return a semicolon-separated description of the failing tasks
	 */
	private String describeFailures(List<String> failingTaskNames) {
		List<String> lines = new ArrayList<>();
		for (String name : failingTaskNames) {
			EvalTask task = this.tasks.stream().filter((t) -> t.name().equals(name)).findFirst().orElse(null);
			if (task == null) {
				lines.add(name);
			}
			else {
				lines.add(task.name() + ": input '" + task.input() + "' — expected output to contain '"
						+ task.expected() + "'");
			}
		}
		return String.join("; ", lines);
	}

	/**
	 * The action whose output is the goal output (the last such step); the last step as a
	 * fallback.
	 * @param pack the pack to inspect
	 * @return the terminal action, or {@code null} when the pack has no steps
	 */
	private ActionStepSpec terminalAction(AgentSpec pack) {
		if (pack.steps().isEmpty()) {
			return null;
		}
		String goalOutput = (pack.goal() != null) ? pack.goal().outputTypeName() : null;
		return pack.steps()
			.stream()
			.filter((s) -> goalOutput != null && goalOutput.equals(s.outputTypeName()))
			.reduce((a, b) -> b)
			.orElseGet(() -> pack.steps().get(pack.steps().size() - 1));
	}

}
