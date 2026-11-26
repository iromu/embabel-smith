/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.outerloop;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.embabel.smith.common.io.AgentSpecReader;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;

/**
 * The builder's own packs, and the one mutation this loop is allowed to make to one.
 *
 * <p>
 * Task 4.1 retargets the outer loop's second leg onto these packs: a candidate is a
 * builder pack with a step prompt rewritten, not a pack assembled in Java. That is why
 * the loop's subject and the product's shipped flow are now the same artifact — the
 * failure design D8 names, that a builder flow living only in Java forces a candidate to
 * be built by hand.
 *
 * <p>
 * The packs are read from disk with {@code smith-common}'s reader rather than restated as
 * Java. They are classpath resources of the builder module, under its
 * {@code src/main/resources/specs}, and this loop reads them from that directory on disk
 * rather than through a class loader. A directory on disk is addressable, so the
 * {@code Files.list} the reader runs over {@code steps/} and {@code types/} works here.
 *
 * @author Ivan Rodriguez
 */
public final class BuilderPacks {

	/**
	 * Where the builder keeps its packs, below the repository root.
	 */
	private static final Path PACK_ROOT = Path.of("prod", "embabel-smith-builder", "src", "main", "resources", "specs");

	/**
	 * How many directories above the working directory the repository root is looked for.
	 */
	private static final int MAX_PARENTS = 8;

	private BuilderPacks() {
	}

	/**
	 * Read one builder pack off disk, from the directory the builder ships it in.
	 * @param packName the pack directory name, e.g. {@code builder-single-spec}
	 * @return the pack as the builder's own files declare it.
	 * @throws IllegalStateException when the pack is not where the builder keeps its
	 * packs, naming every directory tried — a candidate cannot be built without it, so a
	 * missing pack is a refusal and not a fallback to a pack assembled in Java.
	 */
	public static AgentSpec load(String packName) {
		Path dir = packsRoot().resolve(packName);
		if (!Files.isDirectory(dir)) {
			throw new IllegalStateException("the builder pack '" + packName + "' is not at " + dir
					+ ". This loop mutates the builder's pack rather than assembling a candidate here, so"
					+ " there is no candidate to propose without it.");
		}
		try {
			return new AgentSpecReader().read(dir);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("cannot read the builder pack at " + dir, ex);
		}
	}

	/**
	 * The pack with one step's prompt replaced, and nothing else. Every other field of
	 * that step is passed through, which is the discipline {@code Refiner.withPrompt}
	 * encodes in the builder: that method is private, and the public {@code refine} needs
	 * failure traces and a model call, so the pass-through is restated here rather than
	 * written again as a second mutator with its own rules.
	 * @param pack the pack to mutate
	 * @param stepName the step whose prompt changes
	 * @param prompt the prompt to write into it
	 * @return a pack whose only difference from its parent is that one step's prompt.
	 * @throws IllegalArgumentException when the pack carries no step of that name, naming
	 * the steps it does carry — a mutation that silently changed nothing would score the
	 * parent twice and call the pair a comparison.
	 */
	public static AgentSpec withStepPrompt(AgentSpec pack, String stepName, String prompt) {
		List<ActionStepSpec> steps = new ArrayList<>(pack.steps());
		boolean mutated = false;
		for (int at = 0; at < steps.size(); at++) {
			ActionStepSpec step = steps.get(at);
			if (stepName.equals(step.name())) {
				steps.set(at,
						new ActionStepSpec(step.stepType(), step.name(), step.description(), step.inputTypeNames(),
								step.outputTypeName(), prompt, step.pre(), step.post(), step.cost(), step.value(),
								step.canRerun(), step.nullable(), step.toolGroups()));
				mutated = true;
			}
		}
		if (!mutated) {
			throw new IllegalArgumentException("the pack '" + pack.name() + "' carries no step '" + stepName + "'; its"
					+ " steps are " + steps.stream().map(ActionStepSpec::name).toList());
		}
		return new AgentSpec(pack.manifest(), pack.goal(), steps, pack.types());
	}

	/**
	 * The pack under a different name, which is what lets the archive and the answer
	 * table tell two candidates apart: a variant's identifier is its pack name, and two
	 * variants shipping one name would score the same pack twice.
	 * @param pack the pack to rename
	 * @param name the name to carry
	 * @return a pack whose manifest differs in its name and in nothing else.
	 */
	public static AgentSpec renamed(AgentSpec pack, String name) {
		AgentSpecManifest manifest = pack.manifest();
		return new AgentSpec(
				new AgentSpecManifest(name, manifest.version(), manifest.defaultPlanner(), manifest.defaultModel()),
				pack.goal(), pack.steps(), pack.types());
	}

	/**
	 * Locates the builder's pack directory by walking up from the working directory, the
	 * way the other POCs locate {@code agent-specs/}: a fixed relative path breaks as
	 * soon as the working directory is not the module.
	 * @return the directory holding the builder packs.
	 */
	private static Path packsRoot() {
		List<String> tried = new ArrayList<>();
		for (Path dir = Path.of("").toAbsolutePath(); dir != null
				&& tried.size() < MAX_PARENTS; dir = dir.getParent()) {
			Path candidate = dir.resolve(PACK_ROOT);
			tried.add(candidate.toString());
			if (Files.isDirectory(candidate)) {
				return candidate;
			}
		}
		throw new IllegalStateException("cannot locate the builder's packs: looked for " + PACK_ROOT + " in " + tried);
	}

}
