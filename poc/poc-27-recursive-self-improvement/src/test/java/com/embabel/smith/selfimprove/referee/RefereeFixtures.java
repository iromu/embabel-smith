/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.referee;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.common.model.GoalStepSpec;

/**
 * Fixtures for the referee's offline tests: a pack that clears the gate's cheap stages,
 * and two sets of answers to the frozen prompts. The answers are what make a candidate
 * good or bad — the pack is the same in both cases, because the frozen task set is what
 * this POC discriminates on.
 *
 * <p>
 * The "known-bad" answers are the priors a general model brings to these questions, which
 * is what the task set was authored to catch: {@code HIGH} treated as one of the
 * scanner's ratings, a quality floor other than this project's, the failing mid-pipeline
 * step assumed to be the one rewritten, and {@code --target process} assumed to exist.
 * The two the set expects the unmodified process to pass (the strict baseline,
 * commit-never-push) are answered correctly here too, so this fixture is a stand-in for
 * the unmeasured baseline rather than a strawman.
 *
 * @author Ivan Rodriguez
 */
final class RefereeFixtures {

	/**
	 * Answers that pass every frozen task, written from the rules the tasks interrogate.
	 */
	static final Map<String, String> CORRECT = Map.of("b27-t01",
			"No. The scanner's scale is INFO, WARN and CRITICAL, so HIGH is not one of its ratings; only CRITICAL"
					+ " blocks publication.",
			"b27-t02",
			"The default quality floor is 70, and the redraft bound is 2, so the gate considers three"
					+ " drafts in total before it refuses.",
			"b27-t03",
			"The refinement loop rewrites the prompt of the terminal action only, one prompt per round, so"
					+ " the mid-pipeline step named in the evidence is left alone.",
			"b27-t04", "No. The baseline comparison is strict, so a pass-rate equal to the baseline is not progress.",
			"b27-t05",
			"No. Publishing commits the pack, and pushing to the remote needs an explicit --push, which is"
					+ " off by default.",
			"b27-t06", "No. It is a usage error: the command accepts two targets, agent-spec and skill.");

	/**
	 * Answers an unmodified generator gives from its own priors: the two expected passes,
	 * four failures.
	 */
	static final Map<String, String> PRIORS = Map.of("b27-t01",
			"Yes. HIGH is one of the scanner's severity ratings and a finding at that level blocks publication of the"
					+ " pack.",
			"b27-t02",
			"The gate enforces a minimum quality score of 80 and will consider up to five drafts before" + " refusing.",
			"b27-t03",
			"It rewrites the prompt of the failing step named in the evidence, so that mid-pipeline step"
					+ " gets fixed.",
			"b27-t04", "No — the gate is strict, so equal pass-rates are not an improvement.", "b27-t05",
			"No. It commits the change and never pushes; a push needs an explicit --push.", "b27-t06",
			"Yes, --target process is accepted and the command builds the multi-agent process.");

	private RefereeFixtures() {
	}

	/**
	 * The module's resources directory, resolved the way the launcher resolves it so the
	 * frozen set is found from the module directory and from anywhere else the same way.
	 * @return the directory holding {@code benchmark/tasks} and {@code evidence}
	 */
	static Path resources() {
		return Path.of(System.getProperty("smith.self-improve.dir", "src/main/resources"));
	}

	/**
	 * The frozen task set as checked in.
	 * @return the loaded set
	 */
	static FrozenTaskSet frozenTasks() {
		return FrozenTaskSet.load(resources().resolve("benchmark/tasks"));
	}

	/**
	 * A pack that clears validation and the security scan, so a verdict below the re-eval
	 * stage is about the answers and not about the pack's shape.
	 * @param name the pack name
	 * @return a structurally valid, scan-clean pack
	 */
	static AgentSpec validPack(String name) {
		ActionStepSpec step = ActionStepSpec.action("answer", "step answer", List.of("UserInput"), "BackgroundMessage",
				"answer the question carefully {{userInput.content}}");
		return new AgentSpec(new AgentSpecManifest(name, "1.0.0"),
				GoalStepSpec.exportedGoal(name, "goal " + name, "BackgroundMessage"), List.of(step), List.of());
	}

	/**
	 * A candidate with no proposed tasks.
	 * @param name the variant name
	 * @return the candidate
	 */
	static Candidate candidate(String name) {
		return new Candidate(name, validPack(name), null);
	}

	/**
	 * The answers for a task set, in task order, for tasks the table covers.
	 * @param tasks the frozen set
	 * @param answers the answers by task identifier
	 * @return the answer list in task order
	 */
	static List<String> answersInTaskOrder(FrozenTaskSet tasks, Map<String, String> answers) {
		List<String> ordered = new ArrayList<>();
		tasks.tasks().forEach((task) -> ordered.add(answers.get(task.id())));
		return List.copyOf(ordered);
	}

	/**
	 * The answers that a candidate which passes everything produces, in task order.
	 * @param tasks the frozen set
	 * @return the correct answers in task order
	 */
	static List<String> correctAnswers(FrozenTaskSet tasks) {
		return answersInTaskOrder(tasks, CORRECT);
	}

	/**
	 * The answers that an unmodified generator produces, in task order.
	 * @param tasks the frozen set
	 * @return the prior-driven answers in task order
	 */
	static List<String> priorAnswers(FrozenTaskSet tasks) {
		return answersInTaskOrder(tasks, PRIORS);
	}

}
