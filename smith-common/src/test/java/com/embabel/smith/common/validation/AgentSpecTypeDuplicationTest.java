/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.common.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The format names a step's types per definition, so a pack folder whose definitions
 * share one vocabulary carries the same type file once per definition — the shipped
 * {@code skill-interpreter} pack has {@code ActionPlan.yml} under four of its seven
 * definitions. That duplication is what the format asks for, not an oversight, and
 * nothing about the format keeps the copies in step: edit one definition's copy alone and
 * the pack still loads, deploys and validates, with two definitions holding different
 * texts for the type the other names. The drift is silent, which is the whole reason for
 * this test.
 * <p>
 * The comparison is scoped to a pack rather than run over every file sharing a name,
 * because two unrelated packs reusing a name are free to describe it differently:
 * {@code explainer} and {@code compose-extract} each ship a {@code TopicBrief.yml}, and
 * the two differ in their description and are both correct. Per-pack is where a shared
 * type is a shared type.
 * <p>
 * A copy that goes missing is not a failure and is not meant to become one — a pack that
 * drops a definition legitimately drops that definition's copy of the type, and a count
 * here would refuse that for no reason.
 *
 * @author Ivan Rodriguez
 */
class AgentSpecTypeDuplicationTest {

	@Test
	@DisplayName("a type a pack declares twice declares it identically")
	void duplicatedPackTypesAreIdentical() throws IOException {
		Map<String, List<Path>> declaredTypes = typeFilesByPackAndName();

		// The premise, said out loud: a walk that landed anywhere other than the
		// repository's pack tree would group nothing at all, and an empty map satisfies
		// the loop below as happily as a pack that has no duplication.
		assertThat(declaredTypes.values().stream().mapToInt(List::size).sum())
			.as("type files found under the repository's agent-specs directory")
			.isPositive();

		for (List<Path> copies : declaredTypes.values()) {
			for (int first = 0; first < copies.size() - 1; first++) {
				for (int second = first + 1; second < copies.size(); second++) {
					Path declared = copies.get(first);
					Path again = copies.get(second);
					// Files.mismatch answers -1 for two files that agree and otherwise
					// the offset of the first byte that does not match, so the number in
					// the message below is the place to look rather than a count of
					// differences.
					long divergesAt = Files.mismatch(declared, again);
					assertThat(divergesAt)
						.as("%s is declared twice, by %s and by %s, which disagree at byte %d", declared.getFileName(),
								declared, again, divergesAt)
						.isEqualTo(-1L);
				}
			}
		}
	}

	// --- fixtures ------------------------------------------------------------

	/**
	 * Every {@code .yml} file sitting in a {@code types} directory under the repository's
	 * {@code agent-specs/}, keyed by the pack it sits under together with its own file
	 * name. A key holding more than one path is therefore a type that pack declares in
	 * more than one of its definitions, which is the case this test exists for.
	 * @return the type files found, in path order within each key
	 * @throws IOException if the pack tree cannot be walked
	 */
	private static Map<String, List<Path>> typeFilesByPackAndName() throws IOException {
		Path root = agentSpecsRoot();
		Map<String, List<Path>> byPackAndName = new TreeMap<>();
		try (Stream<Path> walk = Files.walk(root)) {
			walk.filter(Files::isRegularFile)
				.filter((file) -> file.getFileName().toString().endsWith(".yml"))
				.filter((file) -> "types".equals(file.getParent().getFileName().toString()))
				.sorted(Comparator.comparing(Path::toString))
				.forEach((file) -> byPackAndName.computeIfAbsent(packAndName(root, file), (key) -> new ArrayList<>())
					.add(file));
		}
		return byPackAndName;
	}

	/**
	 * The grouping key: a pack is the first directory under {@code agent-specs/}, whether
	 * that directory is itself a definition or a folder of definitions.
	 */
	private static String packAndName(Path root, Path file) {
		return root.relativize(file).getName(0) + "/" + file.getFileName();
	}

	/**
	 * Locates {@code agent-specs/} by walking up from the working directory, which is the
	 * module directory under Maven, so a fixed relative path would break as soon as the
	 * build is run from elsewhere. No pack name is used to find it, so a pack added later
	 * is covered the day it lands.
	 * @return the directory holding the repository's checked-in packs
	 */
	private static Path agentSpecsRoot() {
		for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
			Path candidate = dir.resolve("agent-specs");
			if (Files.isDirectory(candidate)) {
				return candidate;
			}
		}
		throw new IllegalStateException("Could not locate the repository's agent-specs directory");
	}

}
