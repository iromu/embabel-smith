/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.skill;

import java.util.List;
import java.util.TreeSet;

import com.embabel.agent.skills.support.InstructionFileReferenceExtractor;

/**
 * The files a published skill folder will contain, read against the files its own
 * {@code SKILL.md} says exist.
 * <p>
 * <b>Why this is checked before publishing.</b> A reader's loader validates a skill's
 * file references as it loads ({@code DefaultDirectorySkillDefinitionLoader},
 * {@code validateFileReferences} on by default), and a reference to a file the folder
 * does not contain does not produce a warning about that file: it produces a skill that
 * is not loaded at all, logged at the reader and nowhere else. So a body that tells the
 * agent to run {@code scripts/x.js} the folder never carried publishes a skill that reads
 * as absent — the worst failure shape this feature has, because everything the builder
 * reports about it stays green. The builder refuses it instead, which is the posture the
 * script scan already takes: a published artifact that cannot do what it says is stopped
 * here, not discovered there.
 * <p>
 * <b>Why the extraction is Embabel's.</b> {@link InstructionFileReferenceExtractor} is
 * the same object the loader uses, so what is refused here is exactly what is refused
 * there — markdown link targets and {@code scripts/}, {@code references/} and
 * {@code assets/} paths, with fenced code blocks stripped so a body that <em>teaches</em>
 * a path is not a body that claims one. Reimplementing that pattern in the builder would
 * be a second source of truth about a rule the framework owns, free to drift from the
 * reader it is meant to predict.
 * <p>
 * The folder's contents are the files {@code SkillPublishable} writes: {@code SKILL.md},
 * {@code evals.json}, the {@code lifecycle.yml} sidecar, and the skill's own scripts.
 * Nothing else — so a body pointing at {@code references/} or {@code assets/} is dangling
 * by construction, which is right: the builder has no way to publish those.
 *
 * @author Ivan Rodriguez
 */
public final class SkillFileReferences {

	/** The files a published skill folder carries whatever the skill itself contains. */
	private static final List<String> ALWAYS_PUBLISHED = List.of("SKILL.md", "evals.json", "lifecycle.yml");

	private SkillFileReferences() {
	}

	/**
	 * The files the body names that the published folder will not contain.
	 * @param skill the skill about to be published
	 * @return the dangling paths, sorted and deduplicated, empty when the folder says
	 * everything the body says
	 */
	public static List<String> missing(Skill skill) {
		TreeSet<String> carried = new TreeSet<>(ALWAYS_PUBLISHED);
		skill.scripts().forEach((script) -> carried.add(script.path()));
		return InstructionFileReferenceExtractor.INSTANCE.extract(skill.body())
			.stream()
			.filter((reference) -> !carried.contains(normalize(reference)))
			.map(SkillFileReferences::normalize)
			.distinct()
			.sorted()
			.toList();
	}

	/**
	 * The path as the folder would resolve it: the extractor already strips a leading
	 * {@code ./}, and a trailing separator means a directory rather than a file the
	 * builder can carry.
	 * @param reference the reference as the body wrote it.
	 * @return the path to compare against the folder.
	 */
	private static String normalize(String reference) {
		String path = reference.trim();
		if (path.startsWith("./")) {
			path = path.substring(2);
		}
		path = path.replaceAll("/+$", "");
		return path.isEmpty() ? reference : path;
	}

}
