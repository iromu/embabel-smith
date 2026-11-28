/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.a2a;

import java.util.List;
import java.util.Set;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.ToolGroupMetadata;
import com.embabel.agent.core.ToolGroupRequirement;
import com.embabel.agent.core.ToolGroupResolution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A host that gains an A2A surface resolves neither coding role.
 *
 * <p>
 * {@code embabel-smith-runtime} declares {@code embabel-smith-tools-coding} at
 * <i>provided</i> scope, and a provided edge stops at the module that declares it — it
 * does not reach a module that depends on the runtime. (Provided rather than optional,
 * and why that choice was made, is settled in {@code docs/05-architecture.md} rather than
 * re-derived here.) This module depends on the runtime and not on the addon, which is the
 * whole reason a deployment that boots an A2A surface is not also a deployment that
 * offers filesystem tools: nothing here registers {@code file} or {@code shell}, so the
 * addon's auto-configuration never runs and a step naming either role is answered by the
 * resolver instead of quietly offered nothing. {@code CodingToolGroupsAbsentTest} in
 * {@code embabel-smith-studio} is the same claim read from the other host that reaches
 * the runtime as a dependency.
 *
 * <p>
 * <b>Why the resolver is asked rather than the classpath alone.</b> A classpath grep says
 * what is present, not what a run can use; the question a pack author cares about is
 * whether {@code resolveToolGroup} answers for the role its step names. So the primary
 * assertions go through the platform, and the classpath check below is the type check
 * that explains the result — the runtime is here, the addon is not.
 *
 * <p>
 * Upstream's own {@code math} role is the control. It is registered by the platform's
 * auto-configuration rather than by anything in this repository, so it resolving here is
 * what stops the two coding assertions from passing because the resolver was never
 * populated or the context came up without one.
 *
 * @author Ivan Rodriguez
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "spring.main.web-application-type=SERVLET", "OPENAI_CUSTOM_BASE_URL=http://127.0.0.1:9/v1",
				"OPENAI_CUSTOM_API_KEY=sk-a2a-test", "OPENAI_CUSTOM_MODELS=a2a-test-model",
				"embabel.models.default-llm=a2a-test-model", })
class CodingToolGroupsAbsentTest {

	private static final List<String> CODING_ROLES = List.of("file", "shell");

	@Autowired
	private AgentPlatform platform;

	@Test
	@DisplayName("the runtime is on the classpath and the coding addon is not")
	void theEdgeUnderTestIsTheProvidedOne() {
		assertThat(present("com.embabel.smith.runtime.SmithRuntimeApplication")).as("runtime on the classpath")
			.isTrue();
		assertThat(present("com.embabel.smith.tools.coding.CodingToolsAutoConfiguration"))
			.as("coding addon on the classpath")
			.isFalse();
	}

	@Test
	void noCodingRoleIsRegistered() {
		assertThat(availableRoles()).as("roles registered in an A2A host").doesNotContainAnyElementsOf(CODING_ROLES);
	}

	@Test
	void eachCodingRoleIsAnsweredWithAnExplanationRatherThanAGroup() {
		for (String role : CODING_ROLES) {
			ToolGroupResolution resolution = resolve(role);

			assertThat(resolution.getResolvedToolGroup()).as("group resolved for role '%s'", role).isNull();
			assertThat(resolution.getFailureMessage()).as("explanation offered for role '%s'", role).isNotBlank();
		}
	}

	@Test
	@DisplayName("the resolver is live: upstream's own role still resolves here")
	void theResolverIsNotMerelyEmpty() {
		assertThat(availableRoles()).as("roles a live resolver registers here").isNotEmpty();
		assertThat(resolve("math").getResolvedToolGroup()).as("upstream's math role").isNotNull();
	}

	private ToolGroupResolution resolve(String role) {
		return this.platform.getToolGroupResolver().resolveToolGroup(new ToolGroupRequirement(role, Set.of(), null));
	}

	private List<String> availableRoles() {
		return this.platform.getToolGroupResolver()
			.availableToolGroups()
			.stream()
			.map(ToolGroupMetadata::getRole)
			.sorted()
			.toList();
	}

	private static boolean present(String className) {
		try {
			Class.forName(className, false, CodingToolGroupsAbsentTest.class.getClassLoader());
			return true;
		}
		catch (ClassNotFoundException ex) {
			return false;
		}
	}

}
