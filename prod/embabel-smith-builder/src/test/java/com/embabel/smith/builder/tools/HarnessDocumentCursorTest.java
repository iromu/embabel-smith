/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.embabel.smith.builder.core.BuilderPackPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two facts the {@code --flow harness} pack's reading loop stands on, stated where
 * the tools live: what its steps are allowed to reach, and what a repeated read costs.
 *
 * <p>
 * <b>Why here rather than in {@code eval}.</b> The cursor is these tools; the harness
 * driver adds a caller, not a capability. What the flow adds on top — that it opens the
 * command line's {@code --docs} roots and starts a fresh memo when it wires its generator
 * — is a CLI fact and is asserted in {@code cli/BuildCommandTest}.
 *
 * <p>
 * <b>The negative half is the point of the reach test.</b> A harness that decides what to
 * read next is a harness whose steps name files, and the reason that is allowed here and
 * was refused for {@code validatePack} (the 116-calls-against-invented-paths failure,
 * {@code docs/12-backlog.md}) is that the name has to be one a listing printed. So the
 * assertion that matters is the refusal, with the successful read of a document inside
 * the roots as its control.
 *
 * @author Ivan Rodriguez
 */
class HarnessDocumentCursorTest {

	/** The three steps {@code builder-harness} ships, in the order the flow runs them. */
	private static final Path STEPS = BuilderPackPaths.directory("builder-harness").resolve("steps");

	private static final List<String> STEP_FILES = List.of("surveyDocumentRoots.yml", "planWhatToReadNext.yml",
			"draftAgentSpecFromRoots.yml");

	@TempDir
	Path workDir;

	/**
	 * The pack's steps ask for the cursor and nothing that could widen it: every step
	 * declares {@code blueprint}, the survey step also declares {@code library}, and no
	 * step declares a name the builder cannot resolve.
	 * {@code tools/ExposedToolGroupsTest} pins the resolvable set from a booted context;
	 * what is new here is that a shipped step of the harness pack would fail this
	 * module's deploy if one of its roles stopped resolving.
	 */
	@Test
	void theHarnessStepsDeclareTheCursorRolesAndNothingElse() throws IOException {
		for (String stepFile : STEP_FILES) {
			Path step = STEPS.resolve(stepFile);
			assertThat(step).as("the step the flow runs").isRegularFile();

			List<String> declared = PackStepFiles.roles(step);
			assertThat(declared).as("roles declared by %s", stepFile).contains("blueprint");
			assertThat(declared).as("roles declared by %s, all of them builder groups", stepFile)
				.isSubsetOf(List.of("blueprint", "library", "validate", "security"));
		}
		assertThat(PackStepFiles.roles(STEPS.resolve("surveyDocumentRoots.yml")))
			.as("the surveying step also sees the library")
			.contains("library");
	}

	/**
	 * The negative: a step that names a document outside the roots the driver opened gets
	 * the refusal and not the file, and the same call aimed inside the roots works — the
	 * control that says the refusal above is containment rather than a broken read.
	 */
	@Test
	void aStepCannotReadOutsideTheRootsTheDriverOpened() throws IOException {
		Path docs = this.workDir.resolve("docs");
		Files.createDirectories(docs);
		Files.writeString(docs.resolve("process.md"), "the process the operator supplied");
		Path outside = this.workDir.resolve("outside.md");
		Files.writeString(outside, "a file no listing of this build printed");

		ToolSurface surface = openedTo(docs);
		assertThat(BlueprintToolGroup.read(surface, outside.toString(), 0))
			.as("the model chooses which document, never where to look")
			.contains("not inside a directory this build was given")
			.doesNotContain("a file no listing of this build printed");
		assertThat(BlueprintToolGroup.read(surface, docs.resolve("process.md").toString(), 0))
			.as("control: a document inside the roots does read")
			.contains("the process the operator supplied");
		assertThat(BlueprintToolGroup.listing(surface)).as("the listing is the only source of names")
			.contains("process.md")
			.doesNotContain("outside.md");
	}

	/**
	 * The cost of a loop that reads: an identical repeat of the same call is answered
	 * from the first rather than re-run, and {@code startRun()} — which the harness
	 * driver calls when it wires its generator — makes the window one build rather than
	 * one process.
	 */
	@Test
	void anIdenticalRepeatIsServedFromTheFirstAndANewRunClearsTheWindow() throws IOException {
		Path docs = this.workDir.resolve("docs");
		Files.createDirectories(docs);
		Files.writeString(docs.resolve("process.md"), "the process, once read");
		String named = docs.resolve("process.md").toString();

		ToolSurface surface = openedTo(docs);
		String first = surface.remembered(BlueprintToolGroup.READ_DOC, doc(named),
				() -> BlueprintToolGroup.read(surface, named, 0));
		String repeat = surface.remembered(BlueprintToolGroup.READ_DOC, doc(named), () -> "read a second time");
		surface.startRun();
		String nextRun = surface.remembered(BlueprintToolGroup.READ_DOC, doc(named), () -> "read next build");

		assertThat(first).doesNotContain("identical to an earlier call").contains("the process, once read");
		assertThat(repeat).as("the second call never reached the body")
			.startsWith("[identical to an earlier call in this run")
			.doesNotContain("read a second time");
		assertThat(nextRun).as("the window is one build, not one process")
			.doesNotContain("identical to an earlier call")
			.contains("read next build");
	}

	private static ToolSurface openedTo(Path root) {
		ToolSurface surface = new ToolSurface();
		surface.openedTo(List.of(root));
		return surface;
	}

	private static String doc(String named) {
		return "{\"document\":\"" + named + "\"}";
	}

}
