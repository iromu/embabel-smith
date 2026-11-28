/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.tools.coding;

import com.embabel.agent.core.ToolGroup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boot state four of four: a switch names a group this module does not contain.
 *
 * <p>
 * <b>This is the case a silent build would swallow.</b> A typo'd or aspirational
 * {@code smith.tools.<group>.enabled} registers nothing, and a run that was offered
 * nothing looks exactly like a run whose group was switched off deliberately. So the
 * switch is reported by name and value rather than ignored, in both spellings an operator
 * plausibly used — the dotted property and the {@code SMITH_TOOLS_X_ENABLED} a container
 * sets.
 *
 * <p>
 * Nothing registers that nobody asked for either: the unknown switch adds a line to the
 * report, not a tool group.
 *
 * @author Ivan Rodriguez
 */
@SpringBootTest(properties = { "spring.main.web-application-type=none", "smith.tools.teleport.enabled=true",
		"SMITH_TOOLS_GIT_ENABLED=true" })
class UnknownSwitchBootStateTest {

	@Autowired
	private ApplicationContext context;

	@Autowired
	private CodingToolsBootReport report;

	@Test
	@DisplayName("an unknown switch registers nothing")
	void unknownGroupRegistersNoBean() {
		assertThat(ToolCalls.roles(this.context.getBeansOfType(ToolGroup.class)))
			.containsExactly(CodingToolGroups.FILE_ROLE);
	}

	@Test
	@DisplayName("the two groups this addon does have are still classified normally")
	void knownGroupsAreUnaffected() {
		assertThat(this.report.registeredByDefault()).containsExactly(CodingToolGroups.FILE_ROLE);
		assertThat(this.report.offByDefault()).containsExactly(CodingToolGroups.SHELL_ROLE);
		assertThat(this.report.switchedOn()).isEmpty();
		assertThat(this.report.switchedOff()).isEmpty();
	}

	@Test
	@DisplayName("both spellings of a switch naming no group this addon contains are named")
	void straySwitchesAreReported() {
		assertThat(this.report.unknownSwitches()).containsEntry("smith.tools.teleport.enabled", "true")
			.containsEntry("SMITH_TOOLS_GIT_ENABLED", "true");
		assertThat(this.report.describe()).contains("switches naming no group this addon contains")
			.contains("smith.tools.teleport.enabled");
	}

}
