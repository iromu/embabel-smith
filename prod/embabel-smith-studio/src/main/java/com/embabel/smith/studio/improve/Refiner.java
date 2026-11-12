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

package com.embabel.smith.studio.improve;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.embabel.smith.builder.eval.PromptRewriter;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.runtime.trace.TraceRecord;

/**
 * Builds a candidate pack by rewriting the prompts of the steps implicated in the
 * failures, using the failure traces as evidence (the POC 9 pattern). Steps without
 * failure evidence are left unchanged, so the candidate differs from the current pack
 * only in the failing steps' prompts (plus the lifecycle metadata the loop adds later).
 *
 * @author Ivan Rodriguez
 */
public class Refiner {

	private final PromptRewriter rewriter;

	public Refiner(PromptRewriter rewriter) {
		this.rewriter = rewriter;
	}

	public Refinement refine(AgentSpec current, List<TraceRecord> failureTraces) {
		Set<String> failing = failingSteps(failureTraces);
		if (failing.isEmpty()) {
			return new Refinement(current, List.of());
		}
		List<ActionStepSpec> steps = new ArrayList<>(current.steps());
		List<String> changed = new ArrayList<>();
		for (int i = 0; i < steps.size(); i++) {
			ActionStepSpec step = steps.get(i);
			if (!failing.contains(step.name())) {
				continue;
			}
			String evidence = evidenceFor(step.name(), failureTraces);
			String newPrompt = this.rewriter.rewrite(step.prompt(), evidence);
			steps.set(i, withPrompt(step, newPrompt));
			changed.add(step.name());
		}
		AgentSpec candidate = new AgentSpec(current.manifest(), current.goal(), steps, current.types());
		return new Refinement(candidate, List.copyOf(changed));
	}

	/**
	 * The names of the steps marked {@code failed} in the traces, in first-seen order.
	 * @param failureTraces the failure traces
	 * @return the names of the failed steps, in first-seen order.
	 */
	public static Set<String> failingSteps(List<TraceRecord> failureTraces) {
		Set<String> out = new LinkedHashSet<>();
		for (TraceRecord record : failureTraces) {
			for (TraceRecord.StepRecord step : record.steps()) {
				if ("failed".equals(step.status())) {
					out.add(step.name());
				}
			}
		}
		return out;
	}

	/**
	 * The failure evidence for a step: the run's input, the step's output excerpt, and
	 * the run's failure reason — the rewrite input the spec requires.
	 * @param stepName the step name to gather evidence for
	 * @param failureTraces the failure traces
	 * @return the failure evidence for the step.
	 */
	private static String evidenceFor(String stepName, List<TraceRecord> failureTraces) {
		List<String> lines = new ArrayList<>();
		for (TraceRecord record : failureTraces) {
			for (TraceRecord.StepRecord step : record.steps()) {
				if (!stepName.equals(step.name())) {
					continue;
				}
				lines.add("run " + record.runId() + ": input '" + record.input() + "'");
				lines.add("step '" + stepName + "' output: " + step.outputExcerpt());
				lines.add("failure: " + record.error());
			}
		}
		return String.join("\n", lines);
	}

	private static ActionStepSpec withPrompt(ActionStepSpec step, String prompt) {
		return new ActionStepSpec(step.stepType(), step.name(), step.description(), step.inputTypeNames(),
				step.outputTypeName(), prompt, step.pre(), step.post(), step.cost(), step.value(), step.canRerun(),
				step.nullable(), step.toolGroups());
	}

	/**
	 * The outcome of a refinement: the candidate pack and the names of the steps whose
	 * prompts were rewritten (empty when nothing needed refining).
	 * @param candidate the refined candidate pack
	 * @param changedSteps the names of the steps whose prompts were rewritten
	 */
	public record Refinement(AgentSpec candidate, List<String> changedSteps) {

		public boolean changed() {
			return !this.changedSteps.isEmpty();
		}
	}

}
