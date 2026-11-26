/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import com.embabel.smith.builder.improve.BuilderPackSurface;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The pack folder the class loader serves holds exactly the files the pack source tree
 * holds: same names, same relative layout, nothing on one side alone.
 *
 * <p>
 * <b>The failure this holds shut.</b> The packs are resources, so
 * {@link BuilderPackPaths#root()} answers {@code target/classes/specs} while the module
 * is built to exploded classes. {@code make build} and {@code make test} do not run
 * {@code clean}, and {@code maven-resources-plugin} copies without pruning — a step file
 * renamed or deleted under {@code src/main/resources/specs} keeps its old copy in the
 * build output indefinitely. Nothing else would notice: tests that read packs through
 * {@code BuilderPackPaths}, and a dev-mode {@code spring-boot:run}, both keep seeing the
 * zombie, so a step the source tree no longer has is still deployed, still planned for,
 * and still scored, while the improvement loop's own surface
 * ({@link BuilderPackSurface#root()}, the sources) no longer contains it. The two seams
 * agree today because nothing has been renamed yet; this test is what keeps them
 * agreeing.
 *
 * <p>
 * <b>What this relies on.</b> No model endpoint, no network. The built folder is whatever
 * this JVM's class loader answers for {@code specs/} — the module's
 * {@code target/classes} for a test run, a temporary jar extraction if the module ever
 * runs from its jar — and the source folder is located from the working directory by
 * directory shape. {@code SMITH_SPECS_DIR} cannot substitute a folder here:
 * {@code SpecFolders} consults the classpath first and that route answers on a JVM, so
 * the override is never reached and the test does not care whether it is set. If
 * {@code BuilderPackSurface} cannot find the source tree it throws, naming every
 * directory it tried, and this test lets it throw — a checkout this process cannot see is
 * a failure, not a reason to skip.
 *
 * <p>
 * <b>How it fails rather than passes quietly.</b> The comparison is two-sided and named:
 * a file present only in the build output, and one present only in the source, land in
 * separate assertions that list the names. An empty listing on either side fails its own
 * non-empty assertion, and the source side additionally has to carry pack manifests so an
 * unrelated directory cannot satisfy it. The two paths are additionally asserted to be
 * different, so a future resolution that made both seams answer the same folder would
 * fail here instead of turning this into a comparison of a directory with itself.
 *
 * @author Ivan Rodriguez
 */
class BuiltPacksMatchTheSourceTest {

	@Test
	void theBuiltPackFolderHoldsExactlyThePackSourceTree() throws IOException {
		Path built = BuilderPackPaths.root();
		Path source = BuilderPackSurface.root();

		assertThat(built.toAbsolutePath())
			.as("the classpath folder and the source folder are two places, so the comparison below has two sides")
			.isNotEqualTo(source.toAbsolutePath());

		Set<String> builtFiles = relativeNames(built);
		Set<String> sourceFiles = relativeNames(source);
		assertThat(sourceFiles).as("pack files under the source tree %s", source).isNotEmpty();
		assertThat(sourceFiles)
			.as("%s holds packs rather than a directory that happens to be named '%s'", source, BuilderPackPaths.ROOT)
			.filteredOn((name) -> name.endsWith("/manifest.yml"))
			.isNotEmpty();
		assertThat(builtFiles).as("pack files under the built folder %s", built).isNotEmpty();

		Set<String> onlyInBuild = new TreeSet<>(builtFiles);
		onlyInBuild.removeAll(sourceFiles);
		assertThat(onlyInBuild)
			.as("pack files the build output %s still holds and the source tree %s no longer has: a zombie the build"
					+ " never deletes (resources are copied, never pruned) that a dev-mode run and every pack-reading"
					+ " test goes on deploying", built, source)
			.isEmpty();

		Set<String> onlyInSource = new TreeSet<>(sourceFiles);
		onlyInSource.removeAll(builtFiles);
		assertThat(onlyInSource)
			.as("pack files the source tree %s has and the build output %s does not: this run would deploy a pack"
					+ " folder missing a step the loop can see and rewrite", source, built)
			.isEmpty();
	}

	/**
	 * The files below a folder, by path relative to it.
	 * @param root the directory to walk, as the seam answered it
	 * @return the relative names of its regular files, sorted, with forward slashes
	 * @throws IOException when the walk fails
	 */
	private static Set<String> relativeNames(Path root) throws IOException {
		try (Stream<Path> walk = Files.walk(root)) {
			Set<String> names = new TreeSet<>();
			walk.filter(Files::isRegularFile)
				.forEach((file) -> names.add(root.relativize(file).toString().replace(File.separatorChar, '/')));
			return names;
		}
	}

}
