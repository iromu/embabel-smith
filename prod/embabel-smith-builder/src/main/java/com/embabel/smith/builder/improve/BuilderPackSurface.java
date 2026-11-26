/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.improve;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.embabel.smith.specsource.FolderSpecSource;
import com.embabel.smith.specsource.SpecSource;

/**
 * The surface the {@link ImprovementLoop} mutates when its subject is the builder's own
 * packs: the pack <em>source</em> files under
 * {@code prod/embabel-smith-builder/src/main/resources/specs}, never the copy the class
 * loader serves.
 *
 * <p>
 * <b>Why not {@code core/BuilderPackPaths}.</b> That class answers where the builder's
 * <em>runs</em> find a pack — {@code target/classes/specs/<pack>} while the module's
 * classes are exploded, a temporary extraction once they travel inside a jar. A loop
 * writing there edits a build artefact: the next {@code mvn} or {@code spring-boot:run}
 * recopies it from {@code src/main/resources/specs}, so a candidate's rewritten step
 * prompt is gone from what the next build in the same process deploys, and the loop has
 * scored an edit that nothing reads. The mutator and the mutated file have to be the same
 * file, which is what this class exists to guarantee.
 *
 * <p>
 * <b>Locating it.</b> The source directory is found by walking up from the working
 * directory — the convention {@code poc-27-recursive-self-improvement}'s
 * {@code outerloop/BuilderPacks} uses for the same directory — so the loop works from the
 * module directory (where Surefire puts it) and from the repository root alike. The
 * directory is named by shape rather than guessed from a class loader, so nothing here
 * can silently return the built copy.
 *
 * <p>
 * <b>Where this refuses.</b> An installed binary carries no source tree, so a process
 * running from one gets a refusal naming every directory tried rather than a fallback to
 * the built copy. A loop that quietly edits a copy is the failure this seam removes, so
 * the refusal is the correct answer for that shape of deployment: run the loop from a
 * checkout.
 *
 * @author Ivan Rodriguez
 */
public final class BuilderPackSurface {

	/**
	 * The pack directory below the builder module itself, which is where Surefire's
	 * working directory already stands.
	 */
	private static final Path PACKS_BELOW_MODULE = Path.of("src", "main", "resources", "specs");

	/**
	 * The pack directory below the repository root, for a loop launched from the checkout
	 * rather than from the module.
	 */
	private static final Path PACKS_BELOW_REPO = Path.of("prod", "embabel-smith-builder", "src", "main", "resources",
			"specs");

	/**
	 * The module directory's own name, which is what makes the first shape above
	 * unambiguous: a parent that is not the builder module is not offered its
	 * {@code src/main/resources}.
	 */
	private static final String BUILDER_MODULE = "embabel-smith-builder";

	/**
	 * How many directories above the working directory the checkout is looked for.
	 */
	private static final int MAX_PARENTS = 8;

	private BuilderPackSurface() {
	}

	/**
	 * The directory holding the builder's pack sources, found from this process's working
	 * directory.
	 * @return the source directory holding every builder pack
	 * @throws IllegalStateException when no builder source tree is above the working
	 * directory, naming every directory tried. An installed binary is the case that
	 * reaches this: the loop mutates the builder's packs in place, so it needs the source
	 * tree, and the built copy the class loader serves is not a surface it may edit.
	 */
	public static Path root() {
		return rootFrom(Path.of(""));
	}

	/**
	 * The loop's surface over the builder's own packs, as the {@link SpecSource}
	 * {@link ImprovementLoop} takes: a folder source rooted at the pack sources, so the
	 * pack the loop reads, the candidate it deploys and the pack its publish writes are
	 * all the same source files.
	 * @return a spec source rooted at the builder's pack source directory.
	 */
	public static SpecSource specSource() {
		Path root = root();
		return new FolderSpecSource("folder:" + root, root);
	}

	/**
	 * As {@link #root()}, starting the walk at a given directory. The seam the tests
	 * drive, so a checkout shape — and the built copy a naive resolution would have
	 * returned — can both be laid out and pointed at.
	 * @param start the directory to walk up from
	 * @return the first directory above {@code start} that holds the builder's pack
	 * sources
	 * @throws IllegalStateException when none does
	 */
	static Path rootFrom(Path start) {
		List<String> tried = new ArrayList<>();
		Path dir = start.toAbsolutePath();
		for (int hop = 0; dir != null && hop < MAX_PARENTS; hop++) {
			for (Path candidate : candidatesBelow(dir)) {
				tried.add(candidate.toString());
				if (Files.isDirectory(candidate)) {
					return candidate;
				}
			}
			dir = dir.getParent();
		}
		throw new IllegalStateException("cannot locate the builder's pack sources: looked for " + PACKS_BELOW_MODULE
				+ " below a directory named " + BUILDER_MODULE + ", and for " + PACKS_BELOW_REPO + " below the"
				+ " repository root, in " + tried + ". The self-improvement loop edits the builder's own packs in"
				+ " place, so it needs the source tree; the built copy the class loader serves"
				+ " (target/classes/specs, or the extraction of a jar) is not the surface it may write, because the"
				+ " next build recopies it. Run the loop from a checkout of this repository.");
	}

	/**
	 * The two shapes the pack source directory takes below one directory: below the
	 * builder module when this one is the module, and below the repository root always.
	 * @param dir the directory to resolve the shapes below
	 * @return the candidate pack directories, module shape first.
	 */
	private static List<Path> candidatesBelow(Path dir) {
		List<Path> candidates = new ArrayList<>(2);
		Path name = dir.getFileName();
		if (name != null && BUILDER_MODULE.equals(name.toString())) {
			candidates.add(dir.resolve(PACKS_BELOW_MODULE));
		}
		candidates.add(dir.resolve(PACKS_BELOW_REPO));
		return candidates;
	}

}
