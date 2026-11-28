/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio;

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
 * The studio resolves neither coding role.
 *
 * <p>
 * This module depends on {@code embabel-smith-runtime}, and the runtime declares
 * {@code embabel-smith-tools-coding} at <i>provided</i> scope — an edge that stops at the
 * declaring module rather than propagating to the modules that depend on it, which is
 * what an absence test like this one can pin. (Provided rather than optional, and why, is
 * settled in {@code docs/05-architecture.md} rather than re-derived here.) So the studio,
 * a UI utility, gets no filesystem tool from the library it borrows the runtime from:
 * nothing here registers {@code file} or {@code shell}, and a step naming either role is
 * answered by the resolver rather than silently offered nothing. A deployment that wants
 * those tools declares the addon itself; it does not inherit them from something that
 * happens to depend on the runtime. {@code CodingToolGroupsAbsentTest} in
 * {@code embabel-smith-a2a-starter} is the same claim from the other such host.
 *
 * <p>
 * <b>Why the resolver is asked and not the dependency tree.</b> A pom grep proves what is
 * present, which is not the question a pack author asks; the question is whether
 * {@code resolveToolGroup} answers for the role a step names, and that is only answerable
 * from a boot. The classpath check below is the type check that names the mechanism
 * behind the answer.
 *
 * <p>
 * Upstream's {@code math} role is the control: it comes from the platform's own
 * auto-configuration rather than from this repository, so it resolving here is what stops
 * the two coding assertions from passing because the resolver was never populated.
 *
 * @author Ivan Rodriguez
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "spring.main.web-application-type=SERVLET", "OPENAI_CUSTOM_BASE_URL=http://127.0.0.1:9/v1",
				"OPENAI_CUSTOM_API_KEY=sk-studio-test", "OPENAI_CUSTOM_MODELS=studio-test-model",
				"embabel.models.default-llm=studio-test-model", })
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
		assertThat(availableRoles()).as("roles registered in the studio").doesNotContainAnyElementsOf(CODING_ROLES);
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
