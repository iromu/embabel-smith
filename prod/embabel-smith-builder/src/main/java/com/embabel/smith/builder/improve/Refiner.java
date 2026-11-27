/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.improve;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.embabel.smith.builder.eval.PromptRewriter;
import com.embabel.smith.builder.publish.PackVersion;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.common.trace.TraceRecord;

/**
 * Builds a candidate pack by rewriting the prompts of the steps implicated in the
 * failures, using the failure traces as evidence (the POC 9 pattern). Steps without
 * failure evidence are left unchanged, so the candidate differs from the current pack
 * only in the failing steps' prompts, in the version it is published at, and in the
 * lifecycle metadata the loop adds later.
 *
 * @author Ivan Rodriguez
 */
public class Refiner {

	private final PromptRewriter rewriter;

	public Refiner(PromptRewriter rewriter) {
		this.rewriter = rewriter;
	}

	public Refinement refine(AgentSpec current, List<TraceRecord> failureTraces) {
		return refine(current, failureTraces, null);
	}

	/**
	 * As {@link #refine(AgentSpec, List)}, asking the rewriter to do the work on one
	 * named LLM.
	 * @param current the pack to refine
	 * @param failureTraces the failure traces driving the rewrite
	 * @param model the LLM to rewrite with, or empty for the configured default
	 * @return the refinement outcome
	 */
	public Refinement refine(AgentSpec current, List<TraceRecord> failureTraces, String model) {
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
			String newPrompt = this.rewriter.rewrite(step.prompt(), evidence, model);
			steps.set(i, withPrompt(step, newPrompt));
			changed.add(step.name());
		}
		AgentSpec candidate = new AgentSpec(nextVersion(current.manifest()), current.goal(), steps, current.types());
		return new Refinement(candidate, List.copyOf(changed));
	}

	/**
	 * The manifest the candidate is published at: the pack it improves, one patch version
	 * up. The improvement is a new version rather than an update of the version it
	 * replaces, which is what lets the store keep both (change: publish-keeps-versions,
	 * decision 7). The baseline's manifest is not modified — the loop reads the baseline
	 * again as the next pass's baseline — and its declared planner and model are carried
	 * across, because the record has no {@code withVersion} and a replacement that
	 * omitted them would drop them silently.
	 * <p>
	 * A pack declaring no version is read as the version the store defaults it to, so its
	 * candidate lands one patch above that. A version whose last segment is not a number
	 * is left as declared: inventing a numeric segment would rewrite a version the
	 * artifact states, and the outcome is then the store's same-version update, which is
	 * the honest answer for an artifact that does not count its own versions.
	 * @param manifest the manifest of the pack being improved, or {@code null} when it
	 * has none
	 * @return the candidate's manifest.
	 */
	private static AgentSpecManifest nextVersion(AgentSpecManifest manifest) {
		if (manifest == null) {
			return null;
		}
		return new AgentSpecManifest(manifest.name(), nextPatch(manifest.version()), manifest.defaultPlanner(),
				manifest.defaultModel());
	}

	/**
	 * The version one patch above {@code version}, where the last dot-separated segment
	 * is the patch.
	 * @param version the declared version, or {@code null} when none is declared
	 * @return the next patch version, or {@code version} unchanged when its last segment
	 * is not a number.
	 */
	private static String nextPatch(String version) {
		String declared = (version == null || version.isBlank()) ? PackVersion.DEFAULT : version.trim();
		int lastDot = declared.lastIndexOf('.');
		String lastSegment = (lastDot < 0) ? declared : declared.substring(lastDot + 1);
		int patch;
		try {
			patch = Integer.parseInt(lastSegment);
		}
		catch (NumberFormatException ex) {
			return declared;
		}
		String prefix = (lastDot < 0) ? "" : declared.substring(0, lastDot + 1);
		return prefix + (patch + 1);
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
				step.nullable(), step.toolGroups(), step.references());
	}

	/**
	 * The outcome of a refinement: the candidate pack and the names of the steps whose
	 * prompts were rewritten (empty when nothing needed refining).
	 *
	 * @param candidate the refined candidate pack
	 * @param changedSteps the names of the steps whose prompts were rewritten
	 */
	public record Refinement(AgentSpec candidate, List<String> changedSteps) {

		public boolean changed() {
			return !this.changedSteps.isEmpty();
		}
	}

}
