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
import com.embabel.smith.common.io.AgentSpecWriter;
import com.embabel.smith.common.samples.SampleAgentSpecs;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code library} group: the packs already in the library, listed and ranked.
 *
 * <p>
 * The library is a directory the <em>driver</em> names, and the group offers a
 * {@code folder:} source only — resolving a {@code git:} source spawns {@code git} and
 * writes a cache, which are two of the four things design D3 keeps out of the tool
 * surface. That is asserted here as a property of the wiring rather than of a model's
 * restraint.
 *
 * @author Ivan Rodriguez
 */
class PackLibraryToolGroupTest {

	@TempDir
	Path workDir;

	private static ToolGroup group(ToolSurface surface) {
		return new PackLibraryToolGroup().libraryTools(surface);
	}

	private static ToolSurface openedTo(Path library) {
		ToolSurface surface = new ToolSurface();
		surface.openedLibrary(library);
		return surface;
	}

	private Path libraryWithOnePack() throws IOException {
		Path library = this.workDir.resolve("library");
		Files.createDirectories(library.resolve("explainer"));
		new AgentSpecWriter().write(SampleAgentSpecs.explainer(), library.resolve("explainer"));
		return library;
	}

	/**
	 * The coupling the bean exists for: the registered role is a name a shipped step
	 * really declares, matched as a {@code toolGroups} list entry rather than as the word
	 * anywhere in the file — that step's prompt discusses the library in prose, and prose
	 * would make this pass without the declaration.
	 * @throws IOException when the pack is not where the other pack tests read it
	 */
	@Test
	void theRoleIsANameTheShippedPackDeclaresAsAToolGroup() throws IOException {
		Path stepFile = BuilderPackPaths.directory("builder-agentic-blueprint")
			.resolve("steps")
			.resolve("surveyBlueprint.yml");

		assertThat(PackStepFiles.declares(stepFile, PackLibraryToolGroup.ROLE))
			.as("step file %s declares '%s' as a tool group", stepFile, PackLibraryToolGroup.ROLE)
			.isTrue();
	}

	@Test
	void theGroupIsRegisteredUnderTheRoleALibraryStepDeclares() {
		assertThat(group(new ToolSurface()).getMetadata().getRole())
			.as("the role a step's toolGroups entry resolves against, and the constant the bean states")
			.isEqualTo(PackLibraryToolGroup.ROLE)
			.isEqualTo("library");
	}

	@Test
	void theGroupCarriesTheTwoLibraryToolsAndNamesTheirArguments() {
		List<Tool> tools = group(new ToolSurface()).getTools();

		assertThat(tools).extracting((tool) -> tool.getDefinition().getName())
			.containsExactlyInAnyOrder(PackLibraryToolGroup.LIST_PACKS, PackLibraryToolGroup.RANK_PACKS);
		assertThat(tools).allSatisfy(
				(tool) -> assertThat(tool.getDefinition().getInputSchema().toJsonSchema()).doesNotContain("arg0"));
		assertThat(tools.get(1).getDefinition().getInputSchema().getParameters()).extracting(Tool.Parameter::getName)
			.containsExactly(PackLibraryToolGroup.QUERY);
	}

	@Test
	void theListingNamesThePacksInTheLibraryTheDriverOpened() throws IOException {
		assertThat(PackLibraryToolGroup.listing(openedTo(libraryWithOnePack()))).contains("explainer")
			.contains("validated=true");
	}

	@Test
	void theListingSaysSoWhenTheBuildWasGivenNoLibrary() {
		assertThat(PackLibraryToolGroup.listing(new ToolSurface())).contains("no pack library");
	}

	@Test
	void theRankingIsTheBuildersOwnScorerAppliedToTheOpenedLibrary() throws IOException {
		String ranking = PackLibraryToolGroup.ranking(openedTo(libraryWithOnePack()), "explainer");

		assertThat(ranking).as("a pack whose name matches the task is reported with its score")
			.contains("explainer")
			.contains("—");
	}

	@Test
	void theRankingRefusesToGuessATaskItWasNotGiven() {
		assertThat(PackLibraryToolGroup.ranking(openedTo(this.workDir), "")).as("no query, no ranking, and the reason")
			.contains(PackLibraryToolGroup.QUERY);
	}

	@Test
	void theRankingIsBoundedToTheHitsItReports() throws IOException {
		assertThat(PackLibraryToolGroup.ranking(openedTo(libraryWithOnePack()), "explainer topic"))
			.as("at most TOP_K hits, and this library holds one")
			.startsWith("1 library packs ranked");
	}

	/**
	 * Offline: reads the registry the context filled from its {@code ToolGroup} beans.
	 */
	@Nested
	@SpringBootTest
	class OnABootedPlatform {

		@Autowired
		AgentPlatform platform;

		@Test
		void theBootedPlatformPublishesTheLibraryGroupAStepCanDeclare() {
			List<String> roles = this.platform.getToolGroupResolver()
				.availableToolGroups()
				.stream()
				.map(ToolGroupDescription::getRole)
				.toList();

			assertThat(roles).as("roles the platform can resolve a toolGroups entry to")
				.contains(PackLibraryToolGroup.ROLE);
		}

	}

}
