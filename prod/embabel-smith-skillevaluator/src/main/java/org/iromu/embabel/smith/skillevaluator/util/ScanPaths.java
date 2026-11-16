/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/** File-tree walking helpers shared by all Tier 1 validators. */
public final class ScanPaths {

	private ScanPaths() {
	}

	/**
	 * Match an entry name against canonical names, exact spelling first and
	 * case-insensitive second (the Python {@code matches_filesystem_name} alias rule,
	 * simplified to casefold matching on the name alone).
	 */
	public static boolean matchesFilesystemName(Path path, Collection<String> canonicalNames) {
		String name = path.getFileName().toString();
		if (canonicalNames.contains(name)) {
			return true;
		}
		String folded = name.toLowerCase(Locale.ROOT);
		for (String canonical : canonicalNames) {
			if (canonical.toLowerCase(Locale.ROOT).equals(folded)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Walk {@code root} and return files whose suffix matches any of {@code extensions}.
	 *
	 * <p>
	 * Port of Python {@code iter_scannable_files}: excluded directories are pruned during
	 * the walk (never descended into), symlinked directories are not followed, and
	 * excluded basenames are skipped case-insensitively. When {@code root} is a file
	 * itself, returns it when its suffix and basename qualify.
	 */
	public static List<Path> iterScannableFiles(Path root, Set<String> extensions, Set<String> excludedDirs,
			Set<String> excludedFiles) {
		List<Path> out = new ArrayList<>();
		if (Files.isRegularFile(root)) {
			if (matchesFilesystemName(root, excludedFiles)) {
				return out;
			}
			String name = root.getFileName().toString();
			if (extensions.stream().anyMatch(name::endsWith)) {
				out.add(root);
			}
			return out;
		}
		if (!Files.isDirectory(root)) {
			return out;
		}
		try {
			Files.walkFileTree(root, Set.of(), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
					if (!dir.equals(root) && matchesFilesystemName(dir, excludedDirs)) {
						return FileVisitResult.SKIP_SUBTREE;
					}
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
					String name = file.getFileName().toString();
					boolean suffixMatch = extensions.stream().anyMatch(name::endsWith);
					if (suffixMatch && !matchesFilesystemName(file, excludedFiles)) {
						out.add(file);
					}
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(Path file, IOException exc) {
					return FileVisitResult.CONTINUE;
				}
			});
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return out;
	}

	public static List<Path> iterScannableFiles(Path root, Set<String> extensions) {
		return iterScannableFiles(root, extensions, Constants.SCAN_EXCLUDED_DIRS, Constants.SCAN_EXCLUDED_FILES);
	}

	/**
	 * Return a child path whose on-disk basename exactly matches {@code filename}, or
	 * null.
	 */
	public static Path findExactChild(Path directory, String filename) {
		try (Stream<Path> children = Files.list(directory)) {
			return children.filter(Files::isRegularFile)
				.filter(child -> child.getFileName().toString().equals(filename))
				.findFirst()
				.orElse(null);
		}
		catch (IOException e) {
			return null;
		}
	}

	/** Locate the skill manifest, preferring canonical exact casing. */
	public static Path findSkillManifest(Path skillPath) {
		for (String name : Constants.SKILL_MANIFEST_VARIANTS) {
			Path manifest = findExactChild(skillPath, name);
			if (manifest != null) {
				return manifest;
			}
		}
		return null;
	}

	public static boolean isSkillDirectory(Path path) {
		return findSkillManifest(path) != null;
	}

	/**
	 * Recursively find all skill directories containing SKILL.md/skill.md files, skipping
	 * manifests beneath {@code SCAN_EXCLUDED_DIRS}. Sorted for stability.
	 */
	public static List<Path> findAllSkills(Path rootPath) {
		List<Path> out = iterScannableFiles(rootPath, Set.of(".md"), Constants.SCAN_EXCLUDED_DIRS, Set.of()).stream()
			.filter(p -> {
				String name = p.getFileName().toString();
				return Constants.SKILL_MANIFEST_VARIANTS.contains(name);
			})
			.map(Path::getParent)
			.distinct()
			.sorted(Comparator.comparing(Path::toString))
			.toList();
		return new ArrayList<>(out);
	}

}
