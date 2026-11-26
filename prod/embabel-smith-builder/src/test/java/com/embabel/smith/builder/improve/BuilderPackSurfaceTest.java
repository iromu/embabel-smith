/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.improve;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.embabel.smith.builder.eval.EvalTask;
import com.embabel.smith.builder.eval.InvokerFactory;
import com.embabel.smith.builder.publish.PublishResult;
import com.embabel.smith.common.io.AgentSpecWriter;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.trace.TraceStore;
import com.embabel.smith.specsource.FolderSpecSource;
import com.embabel.smith.specsource.SpecSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * The surface the improvement loop mutates when its subject is the builder's own packs is
 * the pack <em>source</em> tree,
 * {@code prod/embabel-smith-builder/src/main/resources/specs} — not the copy the class
 * loader serves ({@code target/classes/specs}, or the extraction of a jar).
 *
 * <p>
 * <b>The failure these tests hold shut.</b> {@code core/BuilderPackPaths} answers where a
 * builder <em>run</em> finds a pack, and that answer is a build artefact. A loop pointed
 * there rewrites a file the next build overwrites from the source tree, so a candidate's
 * rewritten step prompt is scored once and then silently reverted — the loop edits a copy
 * and nobody reads it. The loop takes its surface as a {@link SpecSource}
 * ({@code ImprovementLoop#runOnce} resolves it each pass), so the rule can only be held
 * by the code that names that source: {@link BuilderPackSurface}.
 *
 * <p>
 * <b>Why the loop pass is the assertion that matters.</b> A path assertion alone is
 * satisfiable by a surface nothing uses, so the last test runs a real
 * {@link ImprovementLoop} over the surface and reads the prompt it actually deployed. The
 * step file is then edited on disk and the pass re-run: a loop holding a copy, or holding
 * a pack read once and cached, cannot report the edited text.
 *
 * @author Ivan Rodriguez
 */
class BuilderPackSurfaceTest {

	@TempDir
	Path tmp;

	private final AgentSpecWriter writer = new AgentSpecWriter();

	private final InvokerFactory passingInvoker = pack -> input -> "pass";

	private final List<EvalTask> tasks = List.of(new EvalTask("t", "in", "pass"));

	/**
	 * The pack this suite lays out: one step, whose prompt carries the surface marker.
	 */
	private static final String PACK = "extract";

	private static final String STEP = "s1";

	/** Where the packs sit below the builder module, in the shape a checkout has. */
	private static final Path PACKS = Path.of("prod", "embabel-smith-builder", "src", "main", "resources", "specs");

	/** The built copy the class loader would answer with, laid down as the decoy. */
	private static final Path BUILT_PACKS = Path.of("prod", "embabel-smith-builder", "target", "classes", "specs");

	@Test
	void theSurfaceOfThisCheckoutIsThePackSourceTree() throws IOException {
		Path root = BuilderPackSurface.root();

		assertThat(root.toString().replace(java.io.File.separatorChar, '/'))
			.as("the surface is the source tree, naming the module path it resolved")
			.endsWith("prod/embabel-smith-builder/src/main/resources/specs");
		assertThat(root.toString().replace(java.io.File.separatorChar, '/')).doesNotContain("target/classes");
		// The packs the builder actually ships are readable there, so this is the surface
		// and not an empty directory that happens to have the right name.
		assertThat(Files.isRegularFile(root.resolve("builder-spec-one-shot").resolve("manifest.yml"))).isTrue();
	}

	@Test
	void walkingUpFromABuildOutputFindsTheSourceTreeAndNotTheBuiltCopy() throws IOException {
		Path checkout = fakeCheckout("SOURCE-V1");

		// Surefire's working directory is the module, and a run from a build output sits
		// beside the decoy; either way the walk must land on the source tree.
		Path start = checkout.resolve(BUILT_PACKS).getParent();
		Path root = BuilderPackSurface.rootFrom(start);

		assertThat(root).isEqualTo(checkout.resolve(PACKS));
		assertThat(root).isNotEqualTo(checkout.resolve(BUILT_PACKS));
	}

	@Test
	void theLoopReadsTheStepFileItEditsInTheSourceSurface() throws IOException {
		Path checkout = fakeCheckout("SOURCE-V1");
		Path root = BuilderPackSurface.rootFrom(checkout.resolve(BUILT_PACKS).getParent());
		SpecSource surface = new FolderSpecSource("folder:" + root, root);

		TraceStore store = new InMemoryTraceStore();
		store.append(TestFixtures.failedTrace("r1", PACK, "input", TestFixtures.step(STEP, "failed", "bad")));

		List<AgentSpec> deployed = new ArrayList<>();
		runLoop(surface, store, deployed);
		// Mutation "surface resolves to target/classes": the decoy prompt is deployed and
		// this is the assertion that fails.
		assertThat(prompts(deployed)).contains("SOURCE-V1 {{userInput.content}} [revised]");
		assertThat(prompts(deployed)).noneMatch(prompt -> prompt.contains("STALE-BUILT-COPY"));

		// The edit a candidate makes is a write to the source step file. Re-passing the
		// surface must deploy the edited text, which a loop holding the built copy — or
		// holding one read of the pack — cannot do.
		Path stepFile = root.resolve(PACK).resolve("steps").resolve(STEP + ".yml");
		Files.writeString(stepFile, Files.readString(stepFile).replace("SOURCE-V1", "SOURCE-V2"),
				StandardCharsets.UTF_8);

		List<AgentSpec> secondPass = new ArrayList<>();
		runLoop(surface, store, secondPass);

		assertThat(prompts(secondPass)).contains("SOURCE-V2 {{userInput.content}} [revised]");
	}

	@Test
	void anInstalledBinaryWithNoSourceTreeIsRefusedAndNotFallenBackOntoTheBuiltCopy() throws IOException {
		// The shape of an installed deployment: only the built copy exists.
		Path installed = this.tmp.resolve("installed");
		Path packDir = installed.resolve(BUILT_PACKS).resolve(PACK);
		this.writer.write(TestFixtures.packWithPrompt(PACK, STEP, "STALE-BUILT-COPY"), packDir);

		assertThatThrownBy(() -> BuilderPackSurface.rootFrom(installed)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("cannot locate the builder's pack sources")
			.hasMessageContaining("needs the source tree")
			.hasMessageContaining(installed.toString());
	}

	/**
	 * A checkout-shaped tree holding the pack twice: once in the source surface with the
	 * given prompt, once in the build output as the decoy a wrong resolution would
	 * deploy.
	 */
	private Path fakeCheckout(String sourcePrompt) throws IOException {
		Path checkout = this.tmp.resolve("checkout");
		this.writer.write(TestFixtures.packWithPrompt(PACK, STEP, sourcePrompt), checkout.resolve(PACKS).resolve(PACK));
		this.writer.write(TestFixtures.packWithPrompt(PACK, STEP, "STALE-BUILT-COPY"),
				checkout.resolve(BUILT_PACKS).resolve(PACK));
		return checkout;
	}

	/** One held pass over {@code surface}, recording every pack the gate deployed. */
	private void runLoop(SpecSource surface, TraceStore store, List<AgentSpec> deployed) {
		ImprovementLoop loop = new ImprovementLoop(surface, store, (current, feedback) -> current + " [revised]", p -> {
			deployed.add(p);
			return input -> "pass";
		}, this.tasks, (p, overwrite, push) -> new PublishResult("/tmp/" + p.name(), false),
				new LoopPublishPolicy(false, "folder:" + this.tmp, mock(CandidateStore.class)), false);
		loop.runOnce();
	}

	/** The prompts of every step of every pack the loop handed to an invoker. */
	private static List<String> prompts(List<AgentSpec> packs) {
		return packs.stream().flatMap(pack -> pack.steps().stream()).map(step -> step.prompt()).toList();
	}

}
