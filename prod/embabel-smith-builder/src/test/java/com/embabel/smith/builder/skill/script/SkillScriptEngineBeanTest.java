/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.skill.script;

import java.util.List;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.ToolGroupDescription;
import com.embabel.agent.skills.script.SkillScriptExecutionEngine;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The switch, asserted by booting the builder both ways: with no property set the engine
 * bean is not there and the tool list is the list it was before this package existed;
 * with {@code smith.builder.skill-scripts.enabled=true} the engine is published, and the
 * tool list is <em>still</em> that same list.
 *
 * <p>
 * <b>Why two boots.</b> The claim is about a booted context, not about an annotation
 * someone could read as meaning something else. {@code @ConditionalOnProperty} with
 * {@code matchIfMissing} left at its {@code false} default is exactly the kind of
 * one-word setting a later edit flips, and the only assertion that notices is one that
 * boots with the property absent.
 *
 * <p>
 * <b>Why the second boot asserts nothing changed.</b> Installing an engine is not a tool
 * group: the roles a step can declare are the same seven either way, and the script tool
 * appears through {@code Skills.withScriptExecutionEngine}, not through the registry. So
 * the enabled boot proves the engine arrived <em>without</em> widening the surface — the
 * widened reach stays where it belongs, with whoever builds a {@code Skills} that asks
 * for it.
 *
 * <p>
 * Offline: the registry and the bean map are read off a booted context. No model, no
 * endpoint, no network.
 *
 * @author Ivan Rodriguez
 */
@SpringBootTest
class SkillScriptEngineBeanTest {

	/**
	 * The resolvable roles of an ordinary builder boot — the pin
	 * {@code tools/ExposedToolGroupsTest} states.
	 */
	private static final List<String> ORDINARY_TOOL_LIST = List.of("validate", "blueprint", "library", "security",
			"traces", "process", "math");

	@Autowired
	private ApplicationContext context;

	@Autowired
	private AgentPlatform platform;

	@Test
	void noScriptEngineIsInstalledInTheDefaultBuild() {
		assertThat(this.context.getBeanNamesForType(SkillScriptExecutionEngine.class))
			.as("script engines with no property set")
			.isEmpty();
		assertThat(this.context.getBeanNamesForType(GraalJsSkillScriptExecutionEngine.class))
			.as("the GraalJS engine is not there under its own type either")
			.isEmpty();
	}

	@Test
	void theOrdinaryBuildsToolListIsTheListItWas() {
		assertThat(roles()).containsExactlyInAnyOrderElementsOf(ORDINARY_TOOL_LIST);
	}

	@Nested
	@TestPropertySource(properties = SkillScriptEngineConfiguration.ENABLED_PROPERTY + "=true")
	class EngineEnabled {

		@Test
		void theSwitchPublishesTheEngineUnderEmbabelsOwnSeamType() {
			assertThat(context.getBeanNamesForType(SkillScriptExecutionEngine.class))
				.as("script engines with the switch on")
				.isNotEmpty();
			assertThat(context.getBean(SkillScriptExecutionEngine.class))
				.isInstanceOf(GraalJsSkillScriptExecutionEngine.class);
		}

		@Test
		void installingTheEngineAddsNoToolGroupToTheSurface() {
			// Byte-identical to the default boot: the engine is not a ToolGroup, so what
			// a
			// step can declare does not move when the engine arrives.
			assertThat(roles()).containsExactlyInAnyOrderElementsOf(ORDINARY_TOOL_LIST);
		}

	}

	private List<String> roles() {
		return this.platform.getToolGroupResolver()
			.availableToolGroups()
			.stream()
			.map(ToolGroupDescription::getRole)
			.toList();
	}

}
