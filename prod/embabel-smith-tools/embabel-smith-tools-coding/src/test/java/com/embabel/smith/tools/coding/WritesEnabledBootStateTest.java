/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.tools.coding;

import java.nio.file.Path;
import java.util.List;

import com.embabel.agent.core.ToolGroup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The writes switch flipped the other way: what an operator who asked for it gets.
 *
 * <p>
 * This is the positive half of the claim whose negative
 * {@link DefaultBootStateTest#theDefaultSurfaceWritesNothing()} makes, and it is the half
 * that proves the switch is a switch rather than a permanent read-only stance. The
 * offered set is the platform's own write surface — create, write, append, delete — with
 * the addon's targeted edit standing in for upstream's {@code editFile}, so turning
 * writes on does not quietly reintroduce the contract this addon rejected (see
 * {@link CodingFileTools#SUPERSEDED_TOOLS}).
 *
 * <p>
 * The reads are still there, and listed once: {@code readWrite} extends {@code readOnly},
 * so the deduplication in {@link CodingFileTools#tools()} is what stops the same read
 * tool appearing twice in one group.
 *
 * @author Ivan Rodriguez
 */
@SpringBootTest(properties = { "spring.main.web-application-type=none", "smith.tools.coding.writes=true" })
class WritesEnabledBootStateTest {

	private static final Path ROOT = ToolCalls.root("smith-tools-writes-on-");

	@Autowired
	private ApplicationContext context;

	@Autowired
	private CodingToolsBootReport report;

	@DynamicPropertySource
	static void runRoot(DynamicPropertyRegistry registry) {
		registry.add("smith.tools.coding.root", ROOT::toString);
	}

	@Test
	@DisplayName("the writer exists, and so does the deployment's admission that it does")
	void writerIsBuilt() {
		CodingFileTools fileTools = this.context.getBean(CodingFileTools.class);
		assertThat(fileTools.writes()).isTrue();
		assertThat(fileTools.writeTools()).isNotNull();
	}

	@Test
	@DisplayName("the write surface is offered, under these names and no others")
	void writeToolsAreOffered() {
		List<String> offered = ToolCalls.names(group());
		assertThat(offered).contains("createFile", "writeFile", "appendFile", "delete");
		assertThat(ToolCalls.names(group())).filteredOn("editFile"::equals).hasSize(1);
	}

	@Test
	@DisplayName("reads are still offered, and not twice because two instances carry them")
	void readsSurviveTheSwitch() {
		List<String> offered = ToolCalls.names(group());
		assertThat(offered).contains("readFile", "listFiles");
		assertThat(offered).doesNotHaveDuplicates();
	}

	@Test
	@DisplayName("the switch is a switch: the group still arrived by default, not by this property")
	void writesAreNotAGroupSwitch() {
		assertThat(this.report.registeredByDefault()).containsExactly(CodingToolGroups.FILE_ROLE);
		assertThat(this.report.unknownSwitches()).isEmpty();
	}

	private ToolGroup group() {
		return this.context.getBeansOfType(ToolGroup.class)
			.values()
			.stream()
			.filter((candidate) -> candidate.getMetadata().getRole().equals(CodingToolGroups.FILE_ROLE))
			.findFirst()
			.orElseThrow(() -> new AssertionError(
					"no file group; roles " + ToolCalls.roles(this.context.getBeansOfType(ToolGroup.class))));
	}

}
