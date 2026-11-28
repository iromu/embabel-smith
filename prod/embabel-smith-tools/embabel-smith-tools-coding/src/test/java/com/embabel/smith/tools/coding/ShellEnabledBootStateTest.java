/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.tools.coding;

import java.util.Map;

import com.embabel.agent.core.ToolGroup;
import com.embabel.agent.core.ToolGroupPermission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boot state three of four: the operator asked for the group that is off by default.
 *
 * <p>
 * {@code shell} is the case that has to be opt-in, so this test is the one that says the
 * switch actually works rather than the group being permanently absent. The group it
 * registers carries exactly the command tool — a step declaring only {@code shell} must
 * not also be handed the file surface — and it carries the {@code HOST_ACCESS} permission
 * the process-spawning tools need, declared rather than defaulted.
 *
 * @author Ivan Rodriguez
 */
@SpringBootTest(properties = { "spring.main.web-application-type=none", "smith.tools.shell.enabled=true" })
class ShellEnabledBootStateTest {

	@Autowired
	private ApplicationContext context;

	@Autowired
	private CodingToolsBootReport report;

	@Test
	@DisplayName("both roles are now answerable")
	void shellGroupRegistersWhenSwitchedOn() {
		Map<String, ToolGroup> groups = this.context.getBeansOfType(ToolGroup.class);
		assertThat(ToolCalls.roles(groups)).containsExactlyInAnyOrder(CodingToolGroups.FILE_ROLE,
				CodingToolGroups.SHELL_ROLE);
	}

	@Test
	@DisplayName("the shell group carries the command tool and nothing else")
	void shellGroupHoldsOnlyTheCommandTool() {
		ToolGroup shell = group(CodingToolGroups.SHELL_ROLE);
		assertThat(ToolCalls.names(shell)).containsExactly(CommandTool.RUN_COMMAND);
		assertThat(shell.getMetadata().getPermissions()).contains(ToolGroupPermission.HOST_ACCESS);
	}

	@Test
	@DisplayName("the command tool names its arguments, which a Java-authored @LlmTool would not")
	void commandToolDeclaresNamedParameters() {
		assertThat(ToolCalls.parameters(group(CodingToolGroups.SHELL_ROLE), CommandTool.RUN_COMMAND))
			.containsExactly(CommandTool.COMMAND, CommandTool.WORKING_DIRECTORY);
	}

	@Test
	@DisplayName("the report says the role arrived by switch, not by default")
	void reportRecordsTheSwitch() {
		assertThat(this.report.switchedOn()).containsExactly(CodingToolGroups.SHELL_ROLE);
		assertThat(this.report.registeredByDefault()).containsExactly(CodingToolGroups.FILE_ROLE);
		assertThat(this.report.offByDefault()).isEmpty();
	}

	private ToolGroup group(String role) {
		return this.context.getBeansOfType(ToolGroup.class)
			.values()
			.stream()
			.filter((candidate) -> candidate.getMetadata().getRole().equals(role))
			.findFirst()
			.orElseThrow(() -> new AssertionError("no group registered under role '" + role + "'; roles "
					+ ToolCalls.roles(this.context.getBeansOfType(ToolGroup.class))));
	}

}
