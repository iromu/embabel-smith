/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.tools.coding;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.core.ToolConsumer;
import com.embabel.agent.core.ToolGroup;
import com.embabel.agent.core.ToolGroupRequirement;
import com.embabel.agent.spi.ToolGroupResolver;
import com.embabel.agent.spi.support.RegistryToolGroupResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a step's declaration puts in front of the model, as opposed to what the registry
 * holds.
 *
 * <p>
 * <b>The claim, and why the boot tests do not already make it.</b> The boot-state tests
 * assert that a role is answerable in a booted context. That is a different claim from
 * the one the delta makes about a model call: a step declaring only the command role is
 * offered the command tool <em>and no file tool</em>, and a step declaring nothing is
 * offered neither. A registry holding two cleanly-separated groups and an offer list that
 * merges them are compatible with every one of those boot assertions, so the pair is
 * asserted here, at the one function the platform itself uses to turn a declaration into
 * a tool list.
 *
 * <p>
 * <b>That function is the platform's.</b> {@link ToolConsumer#resolveTools} is what the
 * loop calls to assemble the tools for a call: the consumer's own tools, plus the tools
 * of every group its requirements resolve to, deduplicated and sorted. This test supplies
 * the consumer a step amounts to (no tools of its own, one requirement per declared
 * {@code toolGroups} entry) and a resolver built the way the platform builds one, from
 * the {@link ToolGroup} beans in this context. Nothing here re-implements the assembly,
 * so what is listed below is the list a model would be shown, not a paraphrase of it.
 *
 * <p>
 * <b>The resolver is the shape the platform's bean has.</b> This module's classpath is
 * the api alone, so no platform auto-configuration runs here and no resolver bean exists
 * to inject; {@link RegistryToolGroupResolver} over the registered groups is what
 * upstream's own configuration constructs from those same beans. The second channel a
 * group can arrive by, a {@code List<ToolGroup>} provider bean, is what
 * {@code smith.tools.expose.<role>} composes, and it is empty in this boot because
 * nothing sets it here; {@link SearchCapTest} covers the composed path.
 *
 * @author Ivan Rodriguez
 */
@SpringBootTest(properties = { "spring.main.web-application-type=none", "smith.tools.shell.enabled=true" })
class StepOfferListTest {

	private static final Path ROOT = ToolCalls.root("smith-tools-offer-");

	/**
	 * The offered methods that belong to the file surface. Stated in full rather than by
	 * one example, because "no file tool" is only worth reading if it says which tools it
	 * looked for.
	 */
	private static final List<String> FILE_TOOLS = List.of("readFile", "listFiles", "findFiles", "findPatternInProject",
			"writeFile", "createFile", "appendFile", "delete", TargetedEditTool.EDIT_FILE);

	@Autowired
	private ApplicationContext context;

	@DynamicPropertySource
	static void runRoot(DynamicPropertyRegistry registry) {
		registry.add("smith.tools.coding.root", ROOT::toString);
	}

	@Test
	@DisplayName("a step declaring the file role is offered the file tools, and no command")
	void fileRoleOffersFileTools() {
		List<String> offered = offeredTo(CodingToolGroups.FILE_ROLE);
		assertThat(offered).contains("readFile", "listFiles");
		assertThat(offered).doesNotContain(CommandTool.RUN_COMMAND);
	}

	@Test
	@DisplayName("a step declaring only the command role is offered the command tool and no file tool")
	void commandRoleAloneOffersNoFileTool() {
		List<String> offered = offeredTo(CodingToolGroups.SHELL_ROLE);
		assertThat(offered).containsExactly(CommandTool.RUN_COMMAND);
		assertThat(offered).as("the file role is registered in this boot, so the absence below is the offer's")
			.doesNotContainAnyElementsOf(FILE_TOOLS);
	}

	@Test
	@DisplayName("a step declaring both is offered both, which is what makes the absence above a choice")
	void bothRolesOfferBoth() {
		List<String> offered = offeredTo(CodingToolGroups.SHELL_ROLE, CodingToolGroups.FILE_ROLE);
		assertThat(offered).contains(CommandTool.RUN_COMMAND, "readFile");
	}

	@Test
	@DisplayName("a step declaring no tool group is offered neither")
	void noDeclarationOffersNothing() {
		// The control is read in the same breath: the resolver answers both roles when
		// asked, so an empty offer here is the step's declaration and not a dead
		// mechanism. A resolver that resolved nothing would pass this assertion too.
		assertThat(offeredTo(CodingToolGroups.FILE_ROLE)).as("the mechanism is alive").isNotEmpty();
		assertThat(offeredTo(CodingToolGroups.SHELL_ROLE)).as("and alive for the other role").isNotEmpty();
		assertThat(offeredTo()).isEmpty();
	}

	@Test
	@DisplayName("a role nothing registers offers nothing, and the offer list is not where that is said")
	void anUnregisteredRoleOffersNothing() {
		assertThat(offeredTo("shelll")).containsExactly();
		// The offer list going quiet about a role that never resolved is exactly why the
		// run report has to say it. That naming is the runtime's, and asserted there.
		assertThat(offeredTo(CodingToolGroups.SHELL_ROLE)).as("the correctly spelled role resolves").isNotEmpty();
	}

	/**
	 * The tools a step declaring these roles would be offered, assembled by the
	 * platform's own function rather than by this test.
	 * @param roles the {@code toolGroups} entries the step declares
	 * @return the tool names, in the order the platform sorts them
	 */
	private List<String> offeredTo(String... roles) {
		return names(declaredStep(roles).resolveTools(resolver()));
	}

	private static List<String> names(List<Tool> tools) {
		return tools.stream().map((tool) -> tool.getDefinition().getName()).toList();
	}

	private ToolGroupResolver resolver() {
		List<ToolGroup> registered = List.copyOf(this.context.getBeansOfType(ToolGroup.class).values());
		return new RegistryToolGroupResolver("smith-tools-offer", registered);
	}

	/**
	 * A step, as far as the assembly is concerned: a name, no tools of its own, and one
	 * requirement per declared role. The smith runtime's steps reach this shape through
	 * their {@code toolGroups} entries, which is the channel a pack can write.
	 * @param roles the declared roles
	 * @return the consumer to resolve
	 */
	private static ToolConsumer declaredStep(String... roles) {
		final Set<ToolGroupRequirement> requirements = new LinkedHashSet<>();
		for (String role : roles) {
			requirements.add(new ToolGroupRequirement(role, Set.of(), null));
		}
		return new ToolConsumer() {
			@Override
			public String getName() {
				return "declared-step";
			}

			@Override
			public List<Tool> getTools() {
				return List.of();
			}

			@Override
			public Set<ToolGroupRequirement> getToolGroups() {
				return requirements;
			}
		};
	}

}
