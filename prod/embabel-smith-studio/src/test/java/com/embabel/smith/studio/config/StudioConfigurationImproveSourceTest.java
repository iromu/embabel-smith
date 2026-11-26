/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.config;

import java.nio.file.Files;
import java.nio.file.Path;

import com.embabel.smith.builder.improve.BuilderPackSurface;
import com.embabel.smith.common.io.AgentSpecWriter;
import com.embabel.smith.common.samples.SampleAgentSpecs;
import com.embabel.smith.specsource.ResolvedSpec;
import com.embabel.smith.specsource.SpecSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the improvement loop watches when the studio is configured with nothing.
 *
 * <p>
 * <b>The failure these tests hold shut.</b> The bean used to answer an empty temporary
 * folder, which reads as a working studio and improves nothing: every pane is populated,
 * the loop runs clean, and no candidate is ever produced, because the folder it was
 * pointed at has no packs in it. The default is now the builder's own pack
 * <em>sources</em>, so an unconfigured studio runs its loop over the packs that actually
 * ship.
 *
 * <p>
 * <b>Why the source tree and not the built copy.</b> The loop rewrites the pack it is
 * holding, so the file it mutates has to be the file the builder's next build copies
 * from. {@code target/classes/specs} is a build artefact — a candidate written there is
 * reverted by the next {@code mvn} — so the assertion names the source path and rules out
 * the artefact, which is what a routing change through the run-time pack lookup would
 * silently pick up instead. {@code SMITH_SPECS_DIR} is not consulted here either, for the
 * same reason: a read-only fallback is not a surface the loop may write.
 *
 * <p>
 * <b>What the default does not reach.</b> Publishing is a separate bean and still refuses
 * with no configured destination ({@link StudioConfigurationLoopPublisherTest}), so the
 * studio reads the working checkout by default and never publishes into it. The one write
 * that source-tree surface can see is the {@code lifecycle.yml} sidecar the loop stamps
 * into a pack directory when it detects a regression, which that refusal does not gate —
 * and which a clean checkout never gets, because the absent sidecar's baseline is 0.0,
 * the comparison against it is strict, and no {@code lifecycle.yml} ships anywhere under
 * {@code prod/}.
 *
 * @author Ivan Rodriguez
 */
class StudioConfigurationImproveSourceTest {

	private static final Path SOURCE_PACKS = Path.of("prod", "embabel-smith-builder", "src", "main", "resources",
			"specs");

	private final AgentSpecWriter writer = new AgentSpecWriter();

	@Test
	void withNoConfigurationTheLoopWatchesTheBuilderSourcePacks() throws Exception {
		SpecSource source = new StudioConfiguration().improveSource("", "", "");

		String root = source.resolve().origin().root().toString().replace(java.io.File.separatorChar, '/');
		assertThat(root).as("the default surface is the builder's pack sources, named by path")
			.endsWith(SOURCE_PACKS.toString());
		assertThat(root).as("and never the built copy a build would silently recopy").doesNotContain("target/classes");
		// A directory with the right name but nothing in it would satisfy the path
		// assertions, so read a pack through the source the bean actually returned.
		var resolved = source.resolve();
		assertThat(resolved.packs()).as("the shipped packs are readable through it").isNotEmpty();
		assertThat(resolved.packs()).allSatisfy(pack -> assertThat(pack.root()).startsWith(resolved.origin().root()));
	}

	@Test
	void theDefaultSurfaceIsTheOneTheBuilderMutatesInPlace() throws Exception {
		SpecSource source = new StudioConfiguration().improveSource("", "", "");

		assertThat(source.resolve().origin().root()).isEqualTo(BuilderPackSurface.root());
	}

	@Test
	void anExplicitImproveSourceStillWinsOverTheDefault(@TempDir Path library) throws Exception {
		this.writer.write(SampleAgentSpecs.explainer(), library.resolve(SampleAgentSpecs.explainer().name()));
		Path otherSource = Files.createTempDirectory("smith-studio-other-source-");

		SpecSource source = new StudioConfiguration().improveSource("folder:" + library, "folder:" + otherSource, "");

		assertThat(source.resolve().origin().root()).isEqualTo(library);
		assertThat(source.resolve().packs()).extracting(ResolvedSpec::name)
			.containsExactly(SampleAgentSpecs.explainer().name());
	}

	@Test
	void theFirstConfiguredSourceWinsWhenNoImproveSourceIsSet(@TempDir Path library) throws Exception {
		this.writer.write(SampleAgentSpecs.explainer(), library.resolve(SampleAgentSpecs.explainer().name()));
		Path other = Files.createTempDirectory("smith-studio-second-source-");

		SpecSource source = new StudioConfiguration().improveSource("", "folder:" + library + ",folder:" + other, "");

		assertThat(source.resolve().origin().root()).isEqualTo(library);
	}

}
