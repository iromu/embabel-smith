/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.baseline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.embabel.smith.builder.core.SpecPromptCaller;
import com.embabel.smith.selfimprove.referee.FrozenTaskSet;

/**
 * Canned answers for the baseline's offline tests, so neither task 2.4 nor task 2.5 needs
 * a model.
 *
 * <p>
 * The two tables are the same six answers {@code referee.RefereeFixtures} hands the
 * referee — the prior-driven set an unmodified generator produces, and the all-correct
 * set — restated here because that fixture is package-private and this package is not
 * allowed to widen it. They are keyed by task identifier and resolved against the
 * checked-in frozen set, so the {@link #idsMatchTheFrozenSet()} control below fails if
 * the two drift apart, rather than a renamed task quietly becoming an empty answer.
 *
 * @author Ivan Rodriguez
 */
final class BaselineFixtures {

	/**
	 * The divergent answer used to prove the two scoring arms are not the same number.
	 */
	static final String GATE_PASSES_BUT_THE_FULL_CHECK_DOES = "No — equal is not greater than the baseline, and yes"
			+ " that would churn the source.";

	/**
	 * Answers an unmodified generator gives from its own priors: the two the frozen set
	 * expects it to pass, four it was authored to catch.
	 */
	static final Map<String, String> PRIORS = Map.of("b27-t01",
			"Yes. HIGH is one of the scanner's severity ratings and a finding at that level blocks publication of the"
					+ " pack.",
			"b27-t02",
			"The gate enforces a minimum quality score of 80 and will consider up to five drafts before refusing.",
			"b27-t03",
			"It rewrites the prompt of the failing step named in the evidence, so that mid-pipeline step"
					+ " gets fixed.",
			"b27-t04", "No — the gate is strict, so equal pass-rates are not an improvement.", "b27-t05",
			"No. It commits the change and never pushes; a push needs an explicit --push.", "b27-t06",
			"Yes, --target process is accepted and the command builds the multi-agent process.");

	/**
	 * Answers that pass every frozen task, written from the rules the tasks interrogate.
	 */
	static final Map<String, String> CORRECT = Map.of("b27-t01",
			"No. The scanner's scale is INFO, WARN and CRITICAL, so HIGH is not one of its ratings; only CRITICAL"
					+ " blocks publication.",
			"b27-t02",
			"The default quality floor is 70, and the redraft bound is 2, so the gate considers three drafts in total"
					+ " before it refuses.",
			"b27-t03",
			"The refinement loop rewrites the prompt of the terminal action only, one prompt per round, so"
					+ " the mid-pipeline step named in the evidence is left alone.",
			"b27-t04", "No. The baseline comparison is strict, so a pass-rate equal to the baseline is not progress.",
			"b27-t05",
			"No. Publishing commits the pack, and pushing to the remote needs an explicit --push, which is"
					+ " off by default.",
			"b27-t06", "No. It is a usage error: the command accepts two targets, agent-spec and skill.");

	private BaselineFixtures() {
	}

	/**
	 * The module's resources directory, resolved the way the launcher resolves it.
	 * @return the directory holding {@code benchmark/tasks} and {@code evidence}
	 */
	static Path resources() {
		return Path.of(System.getProperty("smith.self-improve.dir", "src/main/resources"));
	}

	/**
	 * The frozen task set as checked in.
	 * @return the loaded set
	 */
	static FrozenTaskSet frozen() {
		return FrozenTaskSet.load(resources().resolve("benchmark/tasks"));
	}

	/**
	 * A resources directory with the checked-in frozen tasks copied into it, so a test
	 * can write evidence beside them and hand the directory to the referee.
	 * @param dir the directory to build the copy in
	 * @return that directory, usable as a resources root
	 */
	static Path resourcesWithFrozenTasks(Path dir) {
		Path target = dir.resolve("benchmark/tasks");
		try {
			Files.createDirectories(target);
			try (var paths = Files.list(resources().resolve("benchmark/tasks"))) {
				paths.filter((path) -> path.getFileName().toString().endsWith(".json"))
					.forEach((path) -> copy(path, target));
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException("cannot copy the frozen tasks into " + dir, ex);
		}
		return dir;
	}

	/**
	 * A caller that answers each frozen prompt from a table and refuses to answer
	 * anything put to it under a system prompt. The refusal is the point: task 2.4's
	 * "unmodified process" means nothing in front of the question, and a caller that
	 * accepted one would let a test pass while measuring something else.
	 * @param tasks the frozen set whose prompts are answered
	 * @param answers the answers by task identifier
	 * @return the canned caller
	 */
	static SpecPromptCaller canned(FrozenTaskSet tasks, Map<String, String> answers) {
		return canned(tasks, answers, null, null);
	}

	/**
	 * As {@link #canned(FrozenTaskSet, Map)}, keeping what the run put in front of the
	 * model so a test can assert on it.
	 * @param tasks the frozen set whose prompts are answered
	 * @param answers the answers by task identifier
	 * @param systems every system message the run supplied, in call order, or null not to
	 * record
	 * @param prompts every prompt the run supplied, in call order, or null not to record
	 * @return the canned caller
	 */
	static SpecPromptCaller canned(FrozenTaskSet tasks, Map<String, String> answers, List<String> systems,
			List<String> prompts) {
		Map<String, String> byPrompt = byPrompt(tasks, answers);
		return (system, user) -> {
			if (system != null) {
				throw new IllegalStateException("the baseline asked a frozen prompt under a system prompt, so the"
						+ " process under test is not unmodified: " + system);
			}
			if (systems != null) {
				systems.add(system);
			}
			if (prompts != null) {
				prompts.add(user);
			}
			return byPrompt.getOrDefault(user, "");
		};
	}

	/**
	 * A caller that answers the same prompt differently each time it is asked, for the
	 * noise floor's varying case. Pass {@code n} tables to get pass 1 from the first,
	 * pass 2 from the second, and every later pass from the last.
	 * @param tasks the frozen set whose prompts are answered
	 * @param passes the answers by task identifier, one table per pass
	 * @return the varying caller
	 */
	static SpecPromptCaller perPass(FrozenTaskSet tasks, List<Map<String, String>> passes) {
		List<Map<String, String>> byPrompt = passes.stream().map((answers) -> byPrompt(tasks, answers)).toList();
		Map<String, Integer> askedTimes = new HashMap<>();
		return (system, user) -> {
			int pass = Math.min(askedTimes.merge(user, 1, Integer::sum) - 1, byPrompt.size() - 1);
			return byPrompt.get(pass).getOrDefault(user, "");
		};
	}

	private static void copy(Path file, Path dir) {
		try {
			Files.copy(file, dir.resolve(file.getFileName()));
		}
		catch (IOException ex) {
			throw new UncheckedIOException("cannot copy " + file, ex);
		}
	}

	private static Map<String, String> byPrompt(FrozenTaskSet tasks, Map<String, String> byTaskId) {
		Map<String, String> byPrompt = new LinkedHashMap<>();
		tasks.tasks().forEach((task) -> byPrompt.put(task.prompt(), byTaskId.getOrDefault(task.id(), "")));
		return byPrompt;
	}

}
