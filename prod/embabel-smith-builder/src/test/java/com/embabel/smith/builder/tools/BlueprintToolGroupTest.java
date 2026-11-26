/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.ToolGroup;
import com.embabel.agent.core.ToolGroupDescription;
import com.embabel.smith.builder.core.BuilderPackPaths;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code blueprint} group: what it offers, and what its bodies will and will not
 * read.
 *
 * <p>
 * The three cases that matter are that the role is the one a step declares; that the
 * tools name their arguments rather than publishing javac's {@code arg0}; and that a
 * document outside the directory the driver opened is refused. The third is the
 * containment the whole surface rests on — the model chooses among documents it was
 * shown, it does not choose where to look — so it is asserted as a body outcome, not as a
 * comment.
 *
 * @author Ivan Rodriguez
 */
class BlueprintToolGroupTest {

	/**
	 * The shipped agentic pack's steps, whose {@code toolGroups} blocks this bean makes
	 * true — the coupling {@code ValidationToolGroupTest} asserts for {@code validate}.
	 */
	private static final Path SURVEY_STEP = BuilderPackPaths.directory("builder-agentic-blueprint")
		.resolve("steps")
		.resolve("surveyBlueprint.yml");

	private static final Path DRAFTING_STEP = BuilderPackPaths.directory("builder-agentic-blueprint")
		.resolve("steps")
		.resolve("draftAgentSpecFromBlueprint.yml");

	@TempDir
	Path workDir;

	private static ToolGroup group(ToolSurface surface) {
		return new BlueprintToolGroup().documentTools(surface);
	}

	private static ToolSurface openedTo(Path root) {
		ToolSurface surface = new ToolSurface();
		surface.openedTo(List.of(root));
		return surface;
	}

	@Test
	void theGroupIsRegisteredUnderTheRoleABlueprintStepDeclares() {
		assertThat(group(new ToolSurface()).getMetadata().getRole())
			.as("the role a step's toolGroups entry resolves against, and the constant the bean states")
			.isEqualTo(BlueprintToolGroup.ROLE)
			.isEqualTo("blueprint");
	}

	@Test
	void theGroupCarriesTheThreeDocumentToolsAndNamesTheirArguments() {
		List<Tool> tools = group(new ToolSurface()).getTools();

		assertThat(tools).extracting((tool) -> tool.getDefinition().getName())
			.containsExactlyInAnyOrder(BlueprintToolGroup.LIST_DOCS, BlueprintToolGroup.READ_DOC,
					BlueprintToolGroup.ASSEMBLE_CORPUS);
		assertThat(tools).allSatisfy(
				(tool) -> assertThat(tool.getDefinition().getInputSchema().toJsonSchema()).doesNotContain("arg0"));
		assertThat(toolNamed(tools, BlueprintToolGroup.READ_DOC).getDefinition().getInputSchema().getParameters())
			.extracting(Tool.Parameter::getName)
			.containsExactly(BlueprintToolGroup.DOCUMENT, BlueprintToolGroup.OFFSET);
	}

	@Test
	void theListingNamesTheDocumentsTheDriverOpenedWithTheirSizes() throws IOException {
		Files.writeString(this.workDir.resolve("a.md"), "# A\n\nSome process prose.");
		Files.writeString(this.workDir.resolve("b.markdown"), "# B");

		String listing = BlueprintToolGroup.listing(openedTo(this.workDir));

		assertThat(listing).contains("a.md").contains("b.markdown").contains("2 documents");
	}

	@Test
	void theListingSaysSoWhenTheBuildWasGivenNoDocuments() {
		assertThat(BlueprintToolGroup.listing(new ToolSurface())).as("an honest absence rather than an empty success")
			.contains("no documents");
	}

	@Test
	void aDocumentOutsideTheOpenedRootIsRefused() throws IOException {
		Path outside = this.workDir.resolve("outside.md");
		Files.writeString(outside, "not part of this build");

		String read = BlueprintToolGroup.read(openedTo(this.workDir.resolve("docs")), outside.toString(), 0);

		assertThat(read).as("the model does not choose where to look")
			.contains("not inside a directory this build " + "was given");
	}

	@Test
	void aListedDocumentIsReturnedWithTheRangeTheStepGot() throws IOException {
		Path docs = this.workDir.resolve("docs");
		Files.createDirectories(docs);
		Files.writeString(docs.resolve("a.md"), "x".repeat(40));

		String read = BlueprintToolGroup.read(openedTo(docs), docs.resolve("a.md").toString(), 0);

		assertThat(read).contains("40 characters, 40 returned from offset 0").contains("xxxx");
	}

	@Test
	void aPagedReadEndsWithAnEndMarkerRatherThanRepeatingTheDocument() throws IOException {
		Path docs = this.workDir.resolve("docs");
		Files.createDirectories(docs);
		Files.writeString(docs.resolve("a.md"), "y".repeat(30));

		String read = BlueprintToolGroup.read(openedTo(docs), docs.resolve("a.md").toString(), 30);

		assertThat(read).startsWith("[end of ").contains("all already returned");
	}

	@Test
	void theCorpusIsTheLoadersOwnTextIncludingItsTruncationMarker() throws IOException {
		Path docs = this.workDir.resolve("docs");
		Files.createDirectories(docs);
		Files.writeString(docs.resolve("a.md"), "# A\n\ncorpus prose");

		assertThat(BlueprintToolGroup.corpus(openedTo(docs))).contains("--- a.md ---").contains("corpus prose");
	}

	@Test
	void anIdenticalRepeatOfACallIsAnsweredFromTheFirstOne() throws IOException {
		Path docs = this.workDir.resolve("docs");
		Files.createDirectories(docs);
		Files.writeString(docs.resolve("a.md"), "z".repeat(20));
		ToolSurface surface = openedTo(docs);

		String first = surface.remembered(BlueprintToolGroup.LIST_DOCS, "{}",
				() -> BlueprintToolGroup.listing(surface));
		String repeat = surface.remembered(BlueprintToolGroup.LIST_DOCS, "{}",
				() -> BlueprintToolGroup.listing(surface));

		assertThat(first).doesNotContain("identical to an earlier call");
		assertThat(repeat).startsWith("[identical to an earlier call in this run");
	}

	/**
	 * The other half of the dedupe, and the half that decides whether it is safe: a call
	 * that differs in any argument runs again. Deduping on the tool name alone would make
	 * a step that paged a document receive its first page forever, and the step would
	 * read that as the document ending where the first screen did.
	 */
	@Test
	void aCallThatDiffersInItsArgumentsIsNotAnsweredFromTheMemo() throws IOException {
		Path docs = this.workDir.resolve("docs");
		Files.createDirectories(docs);
		Files.writeString(docs.resolve("a.md"), "abcdefghij");
		ToolSurface surface = openedTo(docs);
		String named = docs.resolve("a.md").toString();

		String first = surface.remembered(BlueprintToolGroup.READ_DOC,
				"{\"document\":\"" + named + "\",\"offsetChars\":0}", () -> BlueprintToolGroup.read(surface, named, 0));
		String paged = surface.remembered(BlueprintToolGroup.READ_DOC,
				"{\"document\":\"" + named + "\",\"offsetChars\":5}", () -> BlueprintToolGroup.read(surface, named, 5));

		assertThat(first).doesNotContain("identical to an earlier call").contains("abcdefghij");
		assertThat(paged).doesNotContain("identical to an earlier call").contains("fghij");
	}

	@Test
	void aResultLongerThanTheCapIsMarkedAsTruncated() throws IOException {
		Path docs = this.workDir.resolve("docs");
		Files.createDirectories(docs);
		Files.writeString(docs.resolve("big.md"), "w".repeat(ToolOutput.MAX_CHARS + 500));

		assertThat(ToolOutput.cap(BlueprintToolGroup.corpus(openedTo(docs))))
			.contains("[truncated at " + ToolOutput.MAX_CHARS + " characters;");
	}

	@Test
	void aReadThatHitTheCapTellsTheStepHowToContinue() throws IOException {
		Path docs = this.workDir.resolve("docs");
		Files.createDirectories(docs);
		Files.writeString(docs.resolve("big.md"), "q".repeat(ToolOutput.MAX_CHARS + 1_000));

		String read = BlueprintToolGroup.read(openedTo(docs), docs.resolve("big.md").toString(), 0);

		int shown = ToolOutput.MAX_CHARS - BlueprintToolGroup.HEADER_ALLOWANCE;
		assertThat(read).contains(shown + " returned from offset 0")
			.contains("[more remains; call read_doc again with offsetChars=" + shown + "]")
			.hasSizeLessThan(ToolOutput.MAX_CHARS + 64);
	}

	/**
	 * The coupling the bean exists for: the registered role is a name some shipped step
	 * really declares, not a name invented here. Matched as a {@code toolGroups} list
	 * entry rather than as the word anywhere in the file, because these prompts discuss
	 * documents and libraries in prose and prose would make this pass without the
	 * declaration.
	 * @throws IOException when the pack is not where the other pack tests read it
	 */
	@Test
	void theRoleIsANameTheShippedPackDeclaresAsAToolGroup() throws IOException {
		for (Path stepFile : List.of(SURVEY_STEP, DRAFTING_STEP)) {
			assertThat(PackStepFiles.declares(stepFile, BlueprintToolGroup.ROLE))
				.as("step file %s declares '%s' as a tool group", stepFile, BlueprintToolGroup.ROLE)
				.isTrue();
		}
	}

	private static Tool toolNamed(List<Tool> tools, String name) {
		return tools.stream().filter((tool) -> name.equals(tool.getDefinition().getName())).findFirst().orElseThrow();
	}

	/**
	 * Offline for the reason recorded on {@code ValidationToolGroupTest}: this reads the
	 * registry the context filled from its {@code ToolGroup} beans — no model, no
	 * endpoint.
	 */
	@Nested
	@SpringBootTest
	class OnABootedPlatform {

		@Autowired
		AgentPlatform platform;

		@Test
		void theBootedPlatformPublishesTheBlueprintGroupAStepCanDeclare() {
			List<String> roles = this.platform.getToolGroupResolver()
				.availableToolGroups()
				.stream()
				.map(ToolGroupDescription::getRole)
				.toList();

			assertThat(roles).as("roles the platform can resolve a toolGroups entry to")
				.contains(BlueprintToolGroup.ROLE);
		}

	}

}
