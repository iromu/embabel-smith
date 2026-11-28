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
 * Boot state two of four: the operator switched the default-on group off.
 *
 * <p>
 * Switching {@code file} off has to remove the tools as well as the group — a group bean
 * whose backing holder still existed would leave the read log alive for nothing — and the
 * run still has to say something: the boot report records it as switched off, which is
 * the answer that distinguishes a deliberate switch from a group this addon never heard
 * of.
 *
 * @author Ivan Rodriguez
 */
@SpringBootTest(properties = { "spring.main.web-application-type=none", "smith.tools.file.enabled=false" })
class FileDisabledBootStateTest {

	@Autowired
	private ApplicationContext context;

	@Autowired
	private CodingToolsBootReport report;

	@Test
	@DisplayName("no file group, and no file tools behind one")
	void fileSurfaceIsGone() {
		assertThat(ToolCalls.roles(this.context.getBeansOfType(ToolGroup.class))).doesNotContain("file");
		assertThat(this.context.getBeansOfType(ToolGroup.class)).isEmpty();
		assertThat(this.context.getBeansOfType(CodingFileTools.class)).isEmpty();
	}

	@Test
	@DisplayName("shell is still off by default, so the registry is empty rather than shifted")
	void shellIsUnaffected() {
		assertThat(ToolCalls.roles(this.context.getBeansOfType(ToolGroup.class))).doesNotContain("shell");
	}

	@Test
	@DisplayName("off by a switch is reported as off by a switch")
	void reportDistinguishesSwitchedOff() {
		assertThat(this.report.switchedOff()).containsExactly(CodingToolGroups.FILE_ROLE);
		assertThat(this.report.registeredByDefault()).isEmpty();
		assertThat(this.report.offByDefault()).containsExactly(CodingToolGroups.SHELL_ROLE);
	}

}
