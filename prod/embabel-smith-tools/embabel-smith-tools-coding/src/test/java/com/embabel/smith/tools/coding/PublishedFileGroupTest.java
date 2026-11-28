/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.tools.coding;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.core.ToolGroup;
import com.embabel.agent.tools.file.FileReadLog;
import com.embabel.agent.tools.file.PatternSearch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the published {@code file} group actually offers a model, read from a booted
 * context rather than from the source that built it.
 *
 * <p>
 * <b>The three instances are the platform's.</b> The group is {@code FileTools.readOnly},
 * {@code FileTools.readWrite} and a {@code LocalDirectory} standing at the run root, so
 * reading, listing, writing and searching are upstream behaviour this addon neither
 * reimplements nor maintains. The one tool the addon authors for this group is the edit —
 * see {@link EditSemanticsTest} for why upstream's is not offered.
 *
 * <p>
 * <b>How the glob-matcher question fell: no tool was authored.</b> The measurement is
 * asserted here rather than argued: {@code matchesGlob} exists on the platform interface
 * but is not one of its offered methods, so it reaches no model — while path matching is
 * already offered as {@code findFiles(glob)}, which takes a path pattern and returns the
 * matching files. A tool that only answered yes or no about a pattern the model cannot
 * see would add a round-trip for nothing, so the group offers the platform's
 * {@code findFiles} and {@code findPatternInProject} and stops there.
 *
 * <p>
 * <b>Why this boot sets {@code writes}.</b> The surface under test here is the writable
 * one, and an untouched deployment's {@code file} group holds no writer at all — that
 * negative is {@link DefaultBootStateTest}'s to make. What is asserted below about which
 * instances the tools come from, and about there being one edit tool rather than two, is
 * the writable group's shape.
 *
 * @author Ivan Rodriguez
 */
@SpringBootTest(properties = { "spring.main.web-application-type=none", "smith.tools.coding.writes=true" })
class PublishedFileGroupTest {

	private static final Path ROOT = ToolCalls.root("smith-tools-file-group-");

	@Autowired
	private ToolGroup fileGroup;

	@Autowired
	private CodingFileTools fileTools;

	@DynamicPropertySource
	static void runRoot(DynamicPropertyRegistry registry) {
		registry.add("smith.tools.coding.root", ROOT::toString);
	}

	@Test
	@DisplayName("the group answers the role a step declares")
	void groupResolvesUnderTheFileRole() {
		assertThat(this.fileGroup.getMetadata().getRole()).isEqualTo(CodingToolGroups.FILE_ROLE);
		assertThat(this.fileGroup.getMetadata().getDescription()).isNotBlank();
	}

	@Test
	@DisplayName("read, write and edit tools are all listed")
	void groupListsReadWriteAndEditTools() {
		List<String> offered = ToolCalls.names(this.fileGroup);
		assertThat(offered).contains("readFile", "listFiles", "findFiles", "findPatternInProject");
		assertThat(offered).contains("writeFile", "createFile", "appendFile", "delete");
		assertThat(offered).contains(TargetedEditTool.EDIT_FILE);
	}

	@Test
	@DisplayName("the offered tools are the three instances' offered methods, deduplicated")
	void offeredToolsComeFromThePlatformInstances() {
		assertThat(this.fileTools.readTools().getClass().getSimpleName()).isEqualTo("DefaultFileReadTools");
		assertThat(this.fileTools.writeTools().getClass().getSimpleName()).isEqualTo("DefaultFileTools");
		assertThat(this.fileTools.searchTools().getClass().getSimpleName()).isEqualTo("LocalDirectory");
		List<String> upstream = offeredByAllThreeInstances();
		assertThat(ToolCalls.names(this.fileGroup)).isSubsetOf(upstream).doesNotHaveDuplicates();
	}

	@Test
	@DisplayName("path matching is offered, by the platform's own matcher")
	void pathPatternMatchingIsPresent() {
		assertThat(ToolCalls.parameters(this.fileGroup, "findFiles")).contains("glob");
		assertThat(ToolCalls.parameters(this.fileGroup, "findPatternInProject")).contains("pattern");
	}

	@Test
	@DisplayName("matchesGlob exists upstream but is not offered")
	void globPredicateIsNotOffered() throws NoSuchMethodException {
		// The control: the method really exists on the published instance, so the
		// absence below is a fact about what is offered, not about the name being wrong.
		PatternSearch.class.getMethod("matchesGlob", String.class, String.class);
		assertThat(ToolCalls.names(this.fileGroup)).doesNotContain("matchesGlob");
	}

	@Test
	@DisplayName("upstream's editFile is offered by the instance and replaced exactly once")
	void upstreamEditIsSupersededOnce() {
		assertThat(Tool.fromInstance(this.fileTools.writeTools()))
			.anyMatch((tool) -> tool.getDefinition().getName().equals(TargetedEditTool.EDIT_FILE));
		assertThat(ToolCalls.names(this.fileGroup)).filteredOn(TargetedEditTool.EDIT_FILE::equals).hasSize(1);
	}

	@Test
	@DisplayName("a read through the group is recorded on the instance that did it")
	void readsAreRecorded() throws Exception {
		Files.writeString(ROOT.resolve("notes.txt"), "recorded");
		assertThat(ToolCalls.call(ToolCalls.tool(this.fileGroup, "readFile"), "path", "notes.txt"))
			.contains("recorded");
		assertThat(((FileReadLog) this.fileTools.readTools()).getPathsRead()).contains("notes.txt");
	}

	private List<String> offeredByAllThreeInstances() {
		return java.util.stream.Stream
			.of(Tool.fromInstance(this.fileTools.readTools()), Tool.fromInstance(this.fileTools.writeTools()),
					Tool.fromInstance(this.fileTools.searchTools()))
			.flatMap(List::stream)
			.map((tool) -> tool.getDefinition().getName())
			.toList();
	}

}
