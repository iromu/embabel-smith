/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.tools.coding;

import java.util.List;
import java.util.Map;

import com.embabel.agent.core.ToolGroup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boot state one of four: an operator set nothing at all.
 *
 * <p>
 * The claim under test is the documented default, and it is asymmetric on purpose —
 * {@code file} registers because the runtime's default trust tier already names that role
 * and this module has a class behind it; {@code shell} does not, because a jar that
 * arrived by itself must not start offering a process-spawning tool. Both halves are
 * asserted, since a default that is on for both would pass a test that only looked for
 * {@code file}.
 *
 * <p>
 * The third claim is narrower and matters more than the other two: what the group that
 * registers by default can be asked to <em>do</em>. Writes are off with nothing
 * configured, and what is asserted here is that no writer exists to offer rather than
 * that a writer's tools were left out of a list — a deployment holding a hidden writer is
 * still a deployment holding one.
 *
 * @author Ivan Rodriguez
 */
@SpringBootTest(properties = "spring.main.web-application-type=none")
class DefaultBootStateTest {

	/**
	 * The offered methods that change a file. Named rather than inline so the
	 * absence-assertion below states the whole surface it checked, including the
	 * {@code createDirectory} a reader might not think to ask about.
	 */
	private static final List<String> WRITE_TOOLS = List.of("createFile", "writeFile", "editFile", "appendFile",
			"delete", "createDirectory");

	@Autowired
	private ApplicationContext context;

	@Autowired
	private CodingToolsBootReport report;

	@Test
	@DisplayName("file registers with nothing configured")
	void fileGroupRegistersByDefault() {
		assertThat(ToolCalls.roles(this.context.getBeansOfType(ToolGroup.class))).contains("file");
		assertThat(this.context.getBeansOfType(CodingFileTools.class)).hasSize(1);
	}

	@Test
	@DisplayName("shell does not")
	void shellGroupStaysOutOfTheRegistry() {
		assertThat(ToolCalls.roles(this.context.getBeansOfType(ToolGroup.class))).doesNotContain("shell");
	}

	@Test
	@DisplayName("the registered set is exactly the two defaults, not a wider accident")
	void onlyTheDefaultGroupArrives() {
		Map<String, ToolGroup> groups = this.context.getBeansOfType(ToolGroup.class);
		assertThat(groups).hasSize(1);
		assertThat(ToolCalls.roles(groups)).containsExactly(CodingToolGroups.FILE_ROLE);
	}

	@Test
	@DisplayName("with nothing configured, nothing can be asked of the filesystem but to read it")
	void theDefaultSurfaceWritesNothing() {
		Map<String, ToolGroup> groups = this.context.getBeansOfType(ToolGroup.class);
		assertThat(groups).as("the negative is vacuous unless a group is being read").isNotEmpty();
		groups.values()
			.forEach((group) -> assertThat(ToolCalls.names(group)).as("role '%s'", group.getMetadata().getRole())
				.doesNotContainAnyElementsOf(WRITE_TOOLS));
		assertThat(ToolCalls.names(groups.values().iterator().next())).contains("readFile", "listFiles");
	}

	@Test
	@DisplayName("and no writer was built in order to hide it")
	void theDefaultSurfaceHoldsNoWriter() {
		CodingFileTools fileTools = this.context.getBean(CodingFileTools.class);
		assertThat(fileTools.writes()).isFalse();
		assertThat(fileTools.writeTools()).isNull();
	}

	@Test
	@DisplayName("the boot report says how each role arrived, and names no stray switch")
	void reportExplainsBothDefaults() {
		assertThat(this.report.registeredByDefault()).containsExactly(CodingToolGroups.FILE_ROLE);
		assertThat(this.report.offByDefault()).containsExactly(CodingToolGroups.SHELL_ROLE);
		assertThat(this.report.switchedOn()).isEmpty();
		assertThat(this.report.switchedOff()).isEmpty();
		assertThat(this.report.unknownSwitches()).isEmpty();
		assertThat(this.report.describe()).doesNotContain("switches naming no group");
	}

	@Test
	@DisplayName("and with nothing set it attributes neither group to a preset")
	void reportAttributesNothingToAPreset() {
		// The control the preset cases need: the attribution clause exists in an
		// untouched
		// boot too, and says the quiet thing there. A report that named a preset whenever
		// it
		// named a switch would pass a boot that had one and fail this one.
		assertThat(this.report.decidedBy())
			.containsEntry(CodingToolGroups.FILE_ROLE, "no switch set (documented default)")
			.containsEntry(CodingToolGroups.SHELL_ROLE, "no switch set (documented default)");
		assertThat(this.report.unrecognizedPresets()).isEmpty();
		assertThat(this.report.describe()).doesNotContain("preset");
	}

	@Test
	@DisplayName("it claims nothing it did not register, and explains the group that stayed out")
	void reportAttributesAndExplainsSilence() {
		assertThat(this.report.notOurs()).as("nothing in this context arrived from elsewhere").isEmpty();
		assertThat(this.report.registeredNothing()).containsExactly("shell: off by default, nothing asked for it");
		assertThat(this.report.describe()).contains("ours by default [file]")
			.contains("ours disabled by configuration [none]")
			.contains("not ours [none]");
	}

}
