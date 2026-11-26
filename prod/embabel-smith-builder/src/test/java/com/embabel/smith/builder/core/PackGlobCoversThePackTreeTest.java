/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.embabel.smith.builder.improve.BuilderPackSurface;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every file the builder ships as a pack is named by at least one resource glob in
 * {@code META-INF/native-image/com.embabel.smith/builder-native-hints/reachability-metadata.json},
 * so the pairing between the pack tree and the native image breaks in the build instead
 * of in a binary someone downloaded.
 *
 * <p>
 * <b>The failure this holds shut.</b> The hints register the pack folder with two globs —
 * one matching a file directly inside a pack directory, one recursive below it — and they
 * are hand-written on purpose: {@code scripts/native-hints.sh} regenerates the traced
 * file wholesale and none of its runs drives a {@code --flow} pack, so nothing automated
 * would ever list them. Together they reach any {@code .yml} file sitting at least one
 * directory below the pack root, which is the tree as it stands today — 12 files at depth
 * two, 36 at depth three, none deeper — and correct in a way nothing checks. Pack a file
 * under another extension, or put one beside the pack root rather than inside a pack
 * directory, and it matches neither: it is then absent from the image. The JVM run gives
 * no warning, because {@code SpecFolders} lists a real directory there and the file
 * resolves anyway, so every test and every dev run stays green; the binary cannot resolve
 * that file by name; and the CI smoke cannot catch up, because it invokes the image with
 * no subcommand and never reaches a pack flow.
 *
 * <p>
 * <b>Which seam locates the tree, and why.</b> {@link BuilderPackSurface#root()}, not
 * {@link BuilderPackPaths#root()}. The classes under examination are the ones that ship,
 * so reading the built copy here would compare a build artefact against the hints and let
 * a zombie file (one {@code maven-resources-plugin} never deleted, because the build
 * never runs {@code clean}) pass for a source file. {@code BuilderPackSurface} answers
 * the source tree and refuses rather than falling back to {@code target/classes/specs},
 * which is the answer this test wants. The hints file is taken from the same
 * {@code src/main/resources} directory as the walk, so both sides of the comparison come
 * from the checkout.
 *
 * <p>
 * <b>What this relies on.</b> No model endpoint and no network. It needs a checkout above
 * the working directory — {@code BuilderPackSurface} throws, naming every directory it
 * tried, when there is none, and this test lets it: a run that cannot see the pack
 * sources is not a run that can check them, so it fails rather than skipping.
 * {@code SMITH_SPECS_DIR} cannot move the answer, because {@code BuilderPackSurface}
 * locates the directory by shape and never consults that override.
 *
 * <p>
 * <b>How it fails rather than passes quietly.</b> An empty listing fails the file
 * assertion; a directory that is not the pack root fails the manifest assertion sitting
 * next to it; a file no glob registers is named, relative to {@code src/main/resources},
 * in the failure message. Deleting either glob from the hints empties the glob list and
 * fails its own assertion; retyping one so it no longer starts with the pack root does
 * the same, and retyping one so it matches nothing here (an extension the tree does not
 * use) fails the coverage assertion instead.
 *
 * @author Ivan Rodriguez
 */
class PackGlobCoversThePackTreeTest {

	/**
	 * The hand-written hints, addressed below {@code src/main/resources}, beside the
	 * packs they exist to register.
	 */
	private static final Path HINTS = Path.of("META-INF", "native-image", "com.embabel.smith", "builder-native-hints",
			"reachability-metadata.json");

	@Test
	void everyPackFileIsNamedByAResourceGlobInTheNativeHints() throws IOException {
		Path packs = BuilderPackSurface.root();
		Path resources = packs.getParent();
		Path hints = resources.resolve(HINTS);
		assertThat(hints).as("the native hints that ship beside the packs under %s", resources).isRegularFile();

		List<PathMatcher> globs = specsGlobs(hints);
		assertThat(globs).as("resource globs registered for '%s/' in %s", BuilderPackPaths.ROOT, hints).isNotEmpty();

		List<Path> files = packFiles(packs);
		assertThat(files).as("pack files under %s", packs).isNotEmpty();
		assertThat(files)
			.as("%s is a pack root rather than a directory that happens to be named '%s'", packs, BuilderPackPaths.ROOT)
			.filteredOn((file) -> file.endsWith("manifest.yml"))
			.isNotEmpty();

		List<String> uncovered = new ArrayList<>();
		for (Path file : files) {
			Path relative = resources.relativize(file);
			if (globs.stream().noneMatch((glob) -> glob.matches(relative))) {
				uncovered.add(relative.toString().replace(File.separatorChar, '/'));
			}
		}
		assertThat(uncovered)
			.as("pack files no glob in %s registers. A JVM run lists its classpath and never notices their absence"
					+ " from the image; the binary cannot resolve them by name, and the pack flow it is shipped to run"
					+ " is the first thing that finds out.", hints)
			.isEmpty();
	}

	/**
	 * The resource globs the hints register for the pack folder.
	 * @param hints the reachability metadata file to read them from
	 * @return one matcher per glob below the pack root, in file order
	 * @throws IOException when the hints cannot be read
	 */
	private static List<PathMatcher> specsGlobs(Path hints) throws IOException {
		JsonNode resources = new ObjectMapper().readTree(hints.toFile()).path("resources");
		List<PathMatcher> matchers = new ArrayList<>();
		for (JsonNode entry : resources) {
			String glob = entry.path("glob").asText("");
			if (glob.startsWith(BuilderPackPaths.ROOT + "/")) {
				// A PathMatcher pattern is matched against this filesystem's separator,
				// and
				// the hints are written with forward slashes. An identity on POSIX.
				String pattern = glob.replace('/', File.separatorChar);
				matchers.add(FileSystems.getDefault().getPathMatcher("glob:" + pattern));
			}
		}
		return matchers;
	}

	/**
	 * Every regular file below a pack root.
	 * @param packs the directory to walk, absolute
	 * @return the files below it, sorted, empty only when there are none
	 * @throws IOException when the walk fails
	 */
	private static List<Path> packFiles(Path packs) throws IOException {
		try (Stream<Path> walk = Files.walk(packs)) {
			return walk.filter(Files::isRegularFile).sorted().toList();
		}
	}

}
