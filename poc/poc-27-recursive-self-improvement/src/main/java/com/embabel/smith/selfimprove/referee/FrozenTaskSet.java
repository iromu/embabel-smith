/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.referee;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.embabel.smith.builder.eval.EvalTask;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The frozen benchmark task set, read from a directory of task files. It has no writer:
 * nothing in this class can add, remove or edit a task, which is what makes the set
 * read-only to the loop as a property of the code rather than as a rule to remember.
 * {@code evidence/MUTABILITY.md} lists it as the fixed artifact
 * {@code benchmark-task-set}; this class cites that file instead of restating the
 * boundary.
 *
 * <p>
 * The set is also read by {@link #toEvalTasks()} into the shape the product's gate
 * accepts. That conversion is a documented narrowing, because the product's scorer can
 * test one substring per task while a task file can carry {@code require}, {@code anyOf}
 * and {@code forbid} at once. The referee therefore scores the candidate twice — once
 * through the gate, once through the full check here — and reports both numbers rather
 * than letting one stand for the other.
 *
 * @author Ivan Rodriguez
 */
public final class FrozenTaskSet {

	private static final ObjectMapper JSON = new ObjectMapper();

	private final List<FrozenTask> tasks;

	/**
	 * Wraps a task list, refusing a set that could not score anything: an empty set would
	 * make the gate score every candidate 1.0, and a task with no required phrase would
	 * arrive at the gate as an empty expectation, which the product's scorer reads as
	 * "produced any output".
	 * @param tasks the tasks to hold, copied
	 */
	private FrozenTaskSet(List<FrozenTask> tasks) {
		if (tasks.isEmpty()) {
			throw new IllegalStateException("the frozen task set is empty; the gate would score every candidate 1.0");
		}
		List<String> unanchored = tasks.stream()
			.filter((task) -> task.require().isEmpty())
			.map((task) -> task.id() + " declares no require phrase")
			.toList();
		if (!unanchored.isEmpty()) {
			throw new IllegalStateException(
					"frozen tasks carry no expected phrase, so the gate could not test them: " + unanchored);
		}
		this.tasks = List.copyOf(tasks);
	}

	/**
	 * Reads every {@code .json} file in a directory as a task, in file-name order, so the
	 * task order is a property of the checkout rather than of the filesystem.
	 * @param dir the directory holding the frozen task files
	 * @return the task set read from that directory
	 */
	public static FrozenTaskSet load(Path dir) {
		if (!Files.isDirectory(dir)) {
			throw new IllegalStateException("no frozen task set at " + dir + "; the referee cannot score without it");
		}
		List<Path> files;
		try (var paths = Files.list(dir)) {
			files = paths.filter((path) -> path.getFileName().toString().endsWith(".json"))
				.sorted(Comparator.comparing((Path path) -> path.getFileName().toString()))
				.toList();
		}
		catch (IOException ex) {
			throw new UncheckedIOException("cannot list " + dir, ex);
		}
		return new FrozenTaskSet(files.stream().map(FrozenTaskSet::read).toList());
	}

	/**
	 * The tasks as authored.
	 * @return the tasks, in file-name order
	 */
	public List<FrozenTask> tasks() {
		return this.tasks;
	}

	/**
	 * The task identifiers.
	 * @return one identifier per task, in task order
	 */
	public List<String> ids() {
		return this.tasks.stream().map(FrozenTask::id).toList();
	}

	/**
	 * The set in the shape the product's gate takes: one {@link EvalTask} per task, named
	 * by the task's identifier, carrying the task's prompt and its first {@code require}
	 * phrase as the expected substring. The first required phrase is used because a
	 * correct answer must contain it, so the conversion cannot reject a candidate the
	 * frozen check would accept — but it cannot carry an {@code anyOf} alternative or a
	 * {@code forbid} phrase either, which is what {@link #narrowedTaskIds()} reports and
	 * what the referee's report keeps separate from the full score.
	 * @return the gate-shaped task list
	 */
	public List<EvalTask> toEvalTasks() {
		return this.tasks.stream()
			.map((task) -> new EvalTask(task.id(), task.prompt(), task.require().get(0)))
			.toList();
	}

	/**
	 * The tasks whose full check the gate's scorer cannot express.
	 * @return the identifiers of tasks carrying an {@code anyOf} or a {@code forbid}
	 * condition, or more than one required phrase
	 */
	public List<String> narrowedTaskIds() {
		return this.tasks.stream().filter((task) -> !task.expressibleBySubstring()).map(FrozenTask::id).toList();
	}

	/**
	 * The fraction of tasks an answer set passes, scored with the full deterministic
	 * check rather than with the gate's single substring. Answers are taken in task
	 * order; a missing answer scores as a failure, never as a pass.
	 * @param answers the candidate's answers, in task order
	 * @return the pass-rate in {@code [0, 1]}
	 */
	public double score(List<String> answers) {
		return (double) (this.tasks.size() - failedTaskIds(answers).size()) / this.tasks.size();
	}

	/**
	 * The tasks an answer set does not pass, which is the evidence a rejected candidate
	 * is recorded with.
	 * @param answers the candidate's answers, in task order
	 * @return the identifiers of the failing tasks, in task order
	 */
	public List<String> failedTaskIds(List<String> answers) {
		List<String> failed = new ArrayList<>();
		for (int i = 0; i < this.tasks.size(); i++) {
			String answer = (i < answers.size()) ? answers.get(i) : null;
			if (!this.tasks.get(i).passes(answer)) {
				failed.add(this.tasks.get(i).id());
			}
		}
		return List.copyOf(failed);
	}

	/**
	 * Checks the anti-restatement invariant over the whole set before anything is scored,
	 * so a task whose question contains its own answer stops the run instead of quietly
	 * raising the baseline.
	 * @throws IllegalStateException if any task restates one of its own required phrases
	 * in its prompt
	 */
	public void assertNoRestatedPhrases() {
		List<String> offenders = new ArrayList<>();
		for (FrozenTask task : this.tasks) {
			List<String> restated = task.restatedPhrases();
			if (!restated.isEmpty()) {
				offenders.add(task.id() + " restates " + restated);
			}
		}
		if (!offenders.isEmpty()) {
			throw new IllegalStateException("frozen task set can be passed by echoing the question: " + offenders);
		}
	}

	private static FrozenTask read(Path file) {
		try {
			JsonNode node = JSON.readTree(file.toFile());
			return new FrozenTask(text(node, "id"), text(node, "stage"), text(node, "prompt"), phrases(node, "require"),
					phrases(node, "anyOf"), phrases(node, "forbid"), text(node, "check"));
		}
		catch (IOException ex) {
			throw new UncheckedIOException("cannot read the frozen task " + file, ex);
		}
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		if (value == null || value.isNull()) {
			throw new IllegalStateException(field + " is missing from a frozen task file");
		}
		return value.asText();
	}

	private static List<String> phrases(JsonNode node, String field) {
		JsonNode value = node.get(field);
		if (value == null || value.isNull()) {
			return List.of();
		}
		List<String> phrases = new ArrayList<>();
		value.forEach((entry) -> phrases.add(entry.asText()));
		return List.copyOf(phrases);
	}

}
