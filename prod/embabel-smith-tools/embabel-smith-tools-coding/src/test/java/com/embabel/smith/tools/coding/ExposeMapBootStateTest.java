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
 * An operator-composed role, read out of a booted context: what {@code
 * smith.tools.expose.<role>} resolves to is exactly the capabilities it names, and
 * nothing the deployment happened to have lying around.
 *
 * <p>
 * <b>The channel, since it is not the obvious one.</b> These groups arrive as one
 * {@code List<ToolGroup>} bean rather than as one bean per role, because that is the
 * channel upstream built for exactly this —
 * {@code AgentPlatformConfiguration.toolGroupResolver} appends the contents of every such
 * bean, which is how the operator-authored {@code embabel.agent.platform.tools.includes}
 * map reaches the resolver too. So they are deliberately absent from
 * {@code getBeansOfType(ToolGroup.class)} below, and the assertion reads the list bean by
 * name. The consequence worth stating: a composed role never displaces a group this addon
 * owns, because the resolver takes the first role match and the individual beans are
 * already in its list before this one is appended.
 *
 * <p>
 * <b>What {@code file-read} must not include.</b> A role composed of the read capability
 * is the read surface and nothing else — no create, no append, no delete — which is the
 * same promise {@code smith.tools.coding.writes} makes about the default group, made here
 * by naming the capability rather than by a switch.
 *
 * @author Ivan Rodriguez
 */
@SpringBootTest(properties = { "spring.main.web-application-type=none", "smith.tools.expose.review=file-read,search",
		"smith.tools.expose.builder=file-write" })
class ExposeMapBootStateTest {

	private static final Path ROOT = ToolCalls.root("smith-tools-expose-");

	@Autowired
	private ApplicationContext context;

	@Autowired
	private CodingToolsBootReport report;

	@DynamicPropertySource
	static void runRoot(DynamicPropertyRegistry registry) {
		registry.add("smith.tools.coding.root", ROOT::toString);
	}

	@Test
	@DisplayName("one group per named role, and the names are the operator's")
	void eachNamedRoleGetsAGroup() {
		assertThat(roles(composed())).containsExactly("builder", "review");
	}

	@Test
	@DisplayName("the read capability composes the read surface, and no writer")
	void readOnlyCapabilityIsReadOnly() {
		List<String> offered = ToolCalls.names(group("review"));
		assertThat(offered).contains("readFile", "listFiles", "findFiles");
		assertThat(offered).doesNotContainAnyElementsOf(
				List.of("createFile", "writeFile", "appendFile", "delete", "createDirectory", "editFile"));
	}

	@Test
	@DisplayName("the write capability composes writes, with this addon's edit standing in for upstream's")
	void writeCapabilityCarriesTheAddonsEdit() {
		List<String> offered = ToolCalls.names(group("builder"));
		assertThat(offered).contains("createFile", "writeFile", "appendFile", "delete", "readFile");
		assertThat(offered).filteredOn("editFile"::equals).hasSize(1);
		assertThat(offered).doesNotHaveDuplicates();
	}

	@Test
	@DisplayName("a composed tool is confined like the addon's own two")
	void composedToolsRefuseAnEscape() {
		String answer = ToolCalls.call(ToolCalls.tool(group("review"), "readFile"), "path", "../outside.txt");
		assertThat(answer).startsWith("ERROR:").contains("path=../outside.txt").contains("root=" + ROOT);
	}

	@Test
	@DisplayName("they reach the platform as a provider list, not as group beans")
	void theyAreOnTheProviderChannel() {
		assertThat(this.context.getBeansOfType(ToolGroup.class).keySet()).containsExactly("fileToolGroup");
		assertThat(this.context.getBean("exposedToolGroups", List.class)).hasSize(2);
	}

	@Test
	@DisplayName("and the report still calls them ours, though they arrive on somebody else's channel")
	void composedRolesAreNotAttributedToOthers() {
		assertThat(this.report.notOurs()).as("roles this addon composed from the expose map").isEmpty();
		assertThat(this.report.registeredByDefault()).containsExactly(CodingToolGroups.FILE_ROLE);
		assertThat(this.report.describe()).doesNotContain("review", "builder");
	}

	@SuppressWarnings("unchecked")
	private List<ToolGroup> composed() {
		return (List<ToolGroup>) this.context.getBean("exposedToolGroups", List.class);
	}

	private static List<String> roles(List<ToolGroup> groups) {
		return groups.stream().map((group) -> group.getMetadata().getRole()).sorted().toList();
	}

	private ToolGroup group(String role) {
		return composed().stream()
			.filter((candidate) -> candidate.getMetadata().getRole().equals(role))
			.findFirst()
			.orElseThrow(() -> new AssertionError(
					"no composed group under role '" + role + "'; roles " + roles(composed())));
	}

}
