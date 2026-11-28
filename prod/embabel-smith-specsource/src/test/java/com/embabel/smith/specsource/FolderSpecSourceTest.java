/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.specsource;

import com.embabel.smith.common.io.AgentSpecWriter;
import com.embabel.smith.common.samples.SampleAgentSpecs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for folder-source resolution: one-level discovery, validation gating,
 * warnings, and missing-root failure.
 */
class FolderSpecSourceTest {

	@TempDir
	Path root;

	private final AgentSpecWriter writer = new AgentSpecWriter();

	private Path writeExplainer(String dirName) throws IOException {
		return writer.write(SampleAgentSpecs.explainer(), root.resolve(dirName));
	}

	@Test
	void discoversThreePacksOneLevelDeep() throws IOException {
		writeExplainer("alpha");
		writer.write(SampleAgentSpecs.singleStep(), root.resolve("beta"));
		writeExplainer("gamma");
		// not a pack candidate (no manifest.yml) — silently ignored
		Files.createDirectories(root.resolve("notes"));
		Files.writeString(root.resolve("notes").resolve("README.md"), "not a pack");

		ResolvedSpecSource resolved = new FolderSpecSource("folder:" + root, root).resolve();

		assertThat(resolved.packs()).hasSize(3);
		assertThat(resolved.packs()).extracting(ResolvedSpec::name)
			.containsExactly("explainer", "trivial", "explainer");
		assertThat(resolved.packs()).extracting(ResolvedSpec::root)
			.containsExactlyInAnyOrder(root.resolve("alpha"), root.resolve("beta"), root.resolve("gamma"));
		assertThat(resolved.warnings()).isEmpty();
		assertThat(resolved.origin().uri()).isEqualTo("folder:" + root);
		assertThat(resolved.origin().root()).isEqualTo(root);
	}

	@Test
	void invalidPackIsExcludedAndNamedInAWarning() throws IOException {
		writeExplainer("good");
		Path broken = writeExplainer("broken");
		Files.delete(broken.resolve("goal.yml"));

		ResolvedSpecSource resolved = new FolderSpecSource("folder:" + root, root).resolve();

		assertThat(resolved.packs()).hasSize(1);
		assertThat(resolved.packs().get(0).name()).isEqualTo("explainer");
		assertThat(resolved.warnings()).hasSize(1);
		assertThat(resolved.warnings().get(0)).contains("broken").contains("goal.yml");
	}

	@Test
	void emptyFolderResolvesToEmptyPackSetWithWarning() {
		ResolvedSpecSource resolved = new FolderSpecSource("folder:" + root, root).resolve();

		assertThat(resolved.packs()).isEmpty();
		assertThat(resolved.warnings()).hasSize(1);
		assertThat(resolved.warnings().get(0)).contains("no valid agent specs");
	}

	@Test
	void folderWithOnlyInvalidPacksResolvesToEmptyPackSetWithWarnings() throws IOException {
		Path broken = writeExplainer("broken");
		Files.delete(broken.resolve("goal.yml"));

		ResolvedSpecSource resolved = new FolderSpecSource("folder:" + root, root).resolve();

		assertThat(resolved.packs()).isEmpty();
		assertThat(resolved.warnings()).hasSize(2);
	}

	/**
	 * The parity claim between this source and the repository's own examples: what a
	 * folder source finds is exactly the flat half of what a deeper scan finds. Written
	 * against the checked-in corpus rather than a fixture because the gap is a property
	 * of the corpus's shape — the examples are authored both ways, some directly in
	 * {@code agent-specs/} and some inside a folder of definitions — and a fixture would
	 * have to be authored in that shape to say anything, at which point it would be this
	 * directory.
	 */
	@Test
	void theCheckedInCorpusIsDiscoveredOnlyOneLevelDeep() {
		Path specs = repositoryAgentSpecs();

		List<String> discovered = new FolderSpecSource("folder:" + specs, specs).resolve()
			.packs()
			.stream()
			.map((pack) -> relativeTo(specs, pack.root()))
			.toList();

		assertThat(discovered).as("nothing a folder source finds is a definition the deeper scan misses")
			.isSubsetOf(deepDefinitions(specs));
		assertThat(discovered).as("the flat packs are found, so the claim above is not vacuous")
			.contains("arithmetic", "explainer");
		assertThat(discovered).as("a definition one level deeper is not scanned, whatever else is right about it")
			.doesNotContain("coding-agent/change", "skill-interpreter/plan");
	}

	@Test
	void missingRootFailsWithAnErrorNamingThePath() {
		Path missing = root.resolve("does-not-exist");

		assertThatThrownBy(() -> new FolderSpecSource("folder:" + missing, missing).resolve())
			.isInstanceOf(SpecSourceException.class)
			.hasMessageContaining(missing.toString());
	}

	/**
	 * Every directory under the repository's {@code agent-specs/} carrying a
	 * {@code manifest.yml}, to a depth this source does not reach. The same scan
	 * {@code CheckedInAgentSpecsValidateTest} runs over the examples, so the two tests
	 * disagree about nothing but depth.
	 * @param root the corpus to scan
	 * @return the definitions found, in path order
	 */
	private static List<String> deepDefinitions(Path root) {
		try (Stream<Path> walk = Files.walk(root, 3)) {
			return walk.filter(Files::isDirectory)
				.filter((dir) -> Files.isRegularFile(dir.resolve("manifest.yml")))
				.map((dir) -> relativeTo(root, dir))
				.sorted()
				.toList();
		}
		catch (IOException ex) {
			throw new UncheckedIOException("cannot walk " + root, ex);
		}
	}

	/**
	 * Locates {@code agent-specs/} by walking up from the working directory, which is the
	 * module directory under Maven. The twin of the locator in
	 * {@code CheckedInAgentSpecsValidateTest}, kept local so the two tests cannot fail to
	 * compile against each other.
	 * @return the directory holding the repository's example packs
	 */
	private static Path repositoryAgentSpecs() {
		for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
			Path candidate = dir.resolve("agent-specs");
			if (Files.isDirectory(candidate.resolve("explainer"))) {
				return candidate;
			}
		}
		throw new IllegalStateException("Could not locate the repository's agent-specs directory");
	}

	/**
	 * A pack directory as the corpus names it, with forward slashes on every platform.
	 * @param root the corpus root
	 * @param pack a directory under it
	 * @return the relative path
	 */
	private static String relativeTo(Path root, Path pack) {
		return root.relativize(pack).toString().replace('\\', '/');
	}

}
