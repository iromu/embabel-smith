/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.tools.coding;

import java.util.Map;

import com.embabel.agent.core.ToolGroup;
import com.embabel.agent.core.ToolGroupPermission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a preset does, measured as a boot rather than as a map lookup.
 *
 * <p>
 * The claim under test is not "a preset maps to a set of names" — that is a one-line
 * lookup anyone can read. It is that selecting one produces a <em>boot</em> in which the
 * groups arrive under their documented roles, that an explicit switch still decides ahead
 * of it, and that a preset nobody defines leaves nothing half-applied. Each of those is a
 * statement about what the context registered and what the environment resolved, so each
 * is asserted from a running context.
 *
 * <p>
 * <b>The pair that carries the precedence claim.</b> A preset and a switch both end up as
 * {@code smith.tools.<group>.enabled}, and a test that only asserted "the switch won"
 * would pass for an implementation that hardcoded the comparison. So the second case
 * reads both: the preset's own source says {@code true} for the group, the environment
 * says {@code false}, and the group is absent. The preset said what it said, the
 * deployment outranked it, and nothing in this addon had to know the difference — which
 * is the whole design, visible in one pair of reads.
 *
 * @author Ivan Rodriguez
 */
class PresetBootStateTest {

	@Nested
	@SpringBootTest(properties = { "spring.main.web-application-type=none", "smith.tools.preset=coding" })
	@DisplayName("a preset is selected")
	class PresetSelected {

		@Autowired
		private ApplicationContext context;

		@Autowired
		private ConfigurableEnvironment environment;

		@Autowired
		private CodingToolsBootReport report;

		@Test
		@DisplayName("the file and command groups register, under their own roles")
		void thePresetRegistersBothGroups() {
			Map<String, ToolGroup> groups = this.context.getBeansOfType(ToolGroup.class);
			assertThat(ToolCalls.roles(groups)).containsExactlyInAnyOrder(CodingToolGroups.FILE_ROLE,
					CodingToolGroups.SHELL_ROLE);
		}

		@Test
		@DisplayName("and the group it brought in is the command group, not a group that happens to share a name")
		void theGroupItBroughtInCarriesTheCommandTool() {
			ToolGroup shell = this.context.getBeansOfType(ToolGroup.class)
				.values()
				.stream()
				.filter((group) -> CodingToolGroups.SHELL_ROLE.equals(group.getMetadata().getRole()))
				.findFirst()
				.orElseThrow();
			assertThat(ToolCalls.names(shell)).containsExactly(CommandTool.RUN_COMMAND);
			assertThat(shell.getMetadata().getPermissions()).contains(ToolGroupPermission.HOST_ACCESS);
		}

		@Test
		@DisplayName("the switches arrive in a source of their own, written as an operator would write them")
		void thePresetWritesSwitchesRatherThanGates() {
			PropertySource<?> contributed = this.environment.getPropertySources().get(ToolGroupPresets.SOURCE_NAME);
			assertThat(contributed).as("a preset the addon defines leaves its source behind").isNotNull();
			assertThat(contributed.getProperty(
					CodingToolsBootReport.PREFIX + CodingToolGroups.FILE_ROLE + CodingToolsBootReport.ENABLED_SUFFIX))
				.isEqualTo("true");
			assertThat(contributed.getProperty(
					CodingToolsBootReport.PREFIX + CodingToolGroups.SHELL_ROLE + CodingToolsBootReport.ENABLED_SUFFIX))
				.isEqualTo("true");
		}

		@Test
		@DisplayName("the report says the preset decided, not the defaults")
		void reportNamesThePresetAsTheDecidingSource() {
			assertThat(this.report.decidedBy().get(CodingToolGroups.FILE_ROLE)).contains("preset 'coding'");
			assertThat(this.report.decidedBy().get(CodingToolGroups.SHELL_ROLE)).contains("preset 'coding'");
			assertThat(this.report.describe()).contains("preset 'coding'");
			assertThat(this.report.unrecognizedPresets()).isEmpty();
		}

	}

	@Nested
	@SpringBootTest(properties = { "spring.main.web-application-type=none", "smith.tools.preset=coding",
			"smith.tools.shell.enabled=false" })
	@DisplayName("a preset is selected and a switch contradicts it")
	class SwitchBeatsPreset {

		@Autowired
		private ApplicationContext context;

		@Autowired
		private ConfigurableEnvironment environment;

		@Autowired
		private CodingToolsBootReport report;

		@Test
		@DisplayName("the switch decides: the group it turned off does not register")
		void theSwitchWinsTheGroup() {
			assertThat(ToolCalls.roles(this.context.getBeansOfType(ToolGroup.class)))
				.contains(CodingToolGroups.FILE_ROLE)
				.doesNotContain(CodingToolGroups.SHELL_ROLE);
		}

		@Test
		@DisplayName("the preset said true, the deployment outranked it, and this addon never compared the two")
		void precedenceIsTheOrderingNotAComparison() {
			PropertySource<?> contributed = this.environment.getPropertySources().get(ToolGroupPresets.SOURCE_NAME);
			assertThat(contributed).as("the preset still wrote every switch it stands for").isNotNull();
			assertThat(contributed.getProperty(
					CodingToolsBootReport.PREFIX + CodingToolGroups.SHELL_ROLE + CodingToolsBootReport.ENABLED_SUFFIX))
				.as("what the preset asked for")
				.isEqualTo("true");
			assertThat(this.environment.getProperty(
					CodingToolsBootReport.PREFIX + CodingToolGroups.SHELL_ROLE + CodingToolsBootReport.ENABLED_SUFFIX))
				.as("what the boot resolved")
				.isEqualTo("false");
		}

		@Test
		@DisplayName("the report names the switch for that group and the preset for the other")
		void reportAttributesEachGroupSeparately() {
			assertThat(this.report.decidedBy().get(CodingToolGroups.SHELL_ROLE)).doesNotContain("preset")
				.contains(CodingToolsBootReport.PREFIX + CodingToolGroups.SHELL_ROLE
						+ CodingToolsBootReport.ENABLED_SUFFIX);
			assertThat(this.report.decidedBy().get(CodingToolGroups.FILE_ROLE)).contains("preset 'coding'");
			assertThat(this.report.switchedOff()).containsExactly(CodingToolGroups.SHELL_ROLE);
			assertThat(this.report.registeredNothing()).contains("shell: disabled by configuration");
		}

	}

	@Nested
	@SpringBootTest(properties = { "spring.main.web-application-type=none", "smith.tools.preset=video" })
	@DisplayName("a preset naming another addon's bundle is selected")
	class PresetThisAddonDoesNotDefine {

		@Autowired
		private ApplicationContext context;

		@Autowired
		private ConfigurableEnvironment environment;

		@Autowired
		private CodingToolsBootReport report;

		@Test
		@DisplayName("the name is there to be read, which is what makes the silence below a claim")
		void thePresetIsSetInTheBoot() {
			assertThat(this.environment.getProperty(ToolGroupPresets.PRESET_PROPERTY)).isEqualTo("video");
		}

		@Test
		@DisplayName("nothing is applied under it, and the boot is the untouched default")
		void nothingIsHalfApplied() {
			assertThat(this.environment.getPropertySources().get(ToolGroupPresets.SOURCE_NAME))
				.as("an unrecognised preset contributes no source at all, not a partial one")
				.isNull();
			assertThat(ToolCalls.roles(this.context.getBeansOfType(ToolGroup.class)))
				.containsExactly(CodingToolGroups.FILE_ROLE);
		}

		@Test
		@DisplayName("the report says there was nothing here to enable, naming the presets it does define")
		void theRefusalIsReported() {
			assertThat(this.report.unrecognizedPresets()).hasSize(1);
			assertThat(this.report.unrecognizedPresets().get(0)).contains("video")
				.contains(String.valueOf(ToolGroupPresets.names()));
			assertThat(this.report.describe()).contains("presets naming no group this addon contains");
		}

		@Test
		@DisplayName("and the stray-switch check cannot see it, which is why this one exists")
		void theUnknownSwitchListIsBlindToAPreset() {
			assertThat(this.report.unknownSwitches()).as("no .enabled switch was written for another addon").isEmpty();
			assertThat(this.report.unrecognizedPresets()).as("the preset list is the only signal here").isNotEmpty();
		}

	}

}
