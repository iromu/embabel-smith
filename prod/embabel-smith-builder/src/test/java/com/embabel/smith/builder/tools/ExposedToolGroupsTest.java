/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.tools;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.ToolGroup;
import com.embabel.agent.core.ToolGroupDescription;
import com.embabel.agent.spi.support.RegistryToolGroupResolver;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The exposed surface as a tested fact: which tool groups the booted builder can resolve,
 * and what each of them carries.
 *
 * <p>
 * <b>Why this is pinned rather than assumed.</b> A step's {@code toolGroups} entry is a
 * request resolved at prompt time against the registry of {@code ToolGroup} beans, so the
 * honest answer to "what can a builder pack reach?" is a list read off a booted context,
 * not a reading of this module's source. Before this change that list was two roles:
 * {@code validate} (registered by {@link ValidationToolGroup}) and {@code math} —
 * registered by Embabel's own autoconfigure, not by anything in this repository.
 * {@code web}, {@code maps}, {@code github}, {@code browser_automation}, {@code shell},
 * {@code file}, {@code search}, {@code agent} and {@code osx} all resolved to nothing,
 * and they still do.
 *
 * <p>
 * <b>The classpath is not the surface.</b> {@code embabel-agent-api-1.5.4.jar} carries 62
 * {@code com.embabel.agent.tools.*} classes on this module's compile classpath, including
 * {@code tools/file/} and {@code tools/process/} with a shell-capable
 * {@code ExternalProcessTools}. None of them is reachable, because nothing in this
 * context publishes them as a group. That is exactly why the assertion is an <em>exact
 * set</em> and not a containment: an Embabel bump that auto-registers a file- or
 * process-backed group adds a seventh entry and fails this test, which turns a widened
 * reach into a decision somebody had to make. The control that keeps the assertion honest
 * is {@code math} itself — a group this repo never wrote, and the reason a
 * containment-only assertion would have said nothing about upstream registrations at all.
 *
 * <p>
 * <b>Offline.</b> This reads the registry the context filled from its beans. No model, no
 * endpoint, no network — which is why it carries neither {@code @Tag("live")} nor the
 * {@code OPENAI_CUSTOM_BASE_URL} gate that {@code eval/ToolGroupBindingTest}'s booted
 * case needs. Whether a model then <em>calls</em> one of these tools is a different
 * number and stays with the endpoint-gated tests; a registry assertion is not evidence
 * about model behaviour.
 *
 * <p>
 * <b>On resolver identity.</b> The class is stated from {@code getClass()}: the booted
 * resolver's {@code getName()} returns the label {@code SpringBeansToolGroupResolver},
 * which is a string upstream passes as the constructor's {@code name}
 * ({@code AgentPlatformConfiguration.kt:209}) rather than a class — no such class exists
 * in the 1.5.4 jars — so a test that matched on the name would be pinning a string that
 * describes nothing.
 *
 * @author Ivan Rodriguez
 */
@SpringBootTest
class ExposedToolGroupsTest {

	@Autowired
	AgentPlatform platform;

	@Test
	void theBootedResolverIsTheRegistrySpringFillsFromToolGroupBeans() {
		assertThat(this.platform.getToolGroupResolver().getClass().getName())
			.as("the resolver a declared role is resolved against")
			.isEqualTo(RegistryToolGroupResolver.class.getName());
	}

	@Test
	void theResolvableRolesAreExactlyTheBuilderGroupsPlusTheOneUpstreamRegisters() {
		List<String> roles = roles();

		// Literals, deliberately: a set stated through the ROLE constants would move with
		// a
		// rename and stop being a pin. This is the assertion that makes a widened or
		// narrowed
		// surface someone's decision.
		assertThat(roles).as("every role a builder pack's toolGroups entry could resolve to")
			.containsExactlyInAnyOrder("validate", "blueprint", "library", "security", "traces", "process", "math");
	}

	/**
	 * The containment claim, stated as an absence with a control: no group here is backed
	 * by upstream's file or process tools. The control is that the same assertion does
	 * find the groups that <em>are</em> registered, so the list is not empty because the
	 * probe was wrong.
	 */
	@Test
	void noRegisteredGroupIsAFileProcessOrShellGroup() {
		List<String> roles = roles();

		assertThat(roles).as("roles that would hand a step a filesystem, shell or browser tool")
			.noneMatch("file shell browser_automation web maps github search osx agent"::contains);
		assertThat(roles).as("control: the assertion is reading a populated registry").contains("validate", "math");
	}

	@Test
	void everyToolPublishedHereNamesItsArgumentsRatherThanAsJavacNumbersThem() {
		List<String> offenders = groups().stream()
			.filter((group) -> !"math".equals(group.getMetadata().getRole()))
			.flatMap((group) -> group.getTools().stream())
			.filter((tool) -> tool.getDefinition().getInputSchema().toJsonSchema().contains("arg0"))
			.map((tool) -> tool.getDefinition().getName())
			.toList();

		assertThat(offenders).as("tools whose argument schema reads arg0, which binds nothing").isEmpty();
	}

	/**
	 * The other side of the surface: which of these roles a shipped builder step actually
	 * declares. Registered and declared are different facts — an undeclared group reaches
	 * no model because no step asks for it — and the honest answer is that four of the
	 * six builder groups are declared (by {@code builder-single-spec}'s drafting step,
	 * {@code builder-spec-one-shot}'s, and both steps of the agentic pack) while
	 * {@code traces} and {@code process} are published and awaited by the improvement
	 * loop's driver without a shipped step naming them yet. Stating that here rather than
	 * implying it: a reader who assumes all six are in use would be wrong, and the
	 * assumption is one this file could otherwise leave in place.
	 * <p>
	 * Matched from the step files as {@code toolGroups} list entries rather than through
	 * the pack reader, because the claim is about what the shipped text says. The control
	 * is that the same scan does find four names, so an empty list from a mis-aimed walk
	 * cannot pass as "nothing is declared".
	 * @throws IOException when the packs are not where the pack tests read them
	 */
	@Test
	void theRolesShippedStepsDeclareAreFourOfTheSixBuilderGroups() throws IOException {
		List<Path> stepFiles = PackStepFiles.all();
		assertThat(stepFiles).as("shipped builder step files scanned").isNotEmpty();

		List<String> declared = stepFiles.stream()
			.flatMap((file) -> declaredRoles(file).stream())
			.distinct()
			.sorted()
			.toList();

		assertThat(declared).as("group names shipped builder steps declare")
			.containsExactly("blueprint", "library", "security", "validate");
		assertThat(roles()).as("every declared name is also resolvable, so none of these is a typo")
			.containsAll(declared);
		assertThat(roles()).as("registered and awaiting a step that names them").contains("traces", "process");
	}

	private List<String> declaredRoles(Path stepFile) {
		try {
			return PackStepFiles.roles(stepFile);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Unreadable step file " + stepFile, ex);
		}
	}

	private List<String> roles() {
		return this.platform.getToolGroupResolver()
			.availableToolGroups()
			.stream()
			.map(ToolGroupDescription::getRole)
			.toList();
	}

	private List<ToolGroup> groups() {
		var resolver = this.platform.getToolGroupResolver();
		assertThat(resolver).as("the registry the groups are read from").isInstanceOf(RegistryToolGroupResolver.class);
		return List.copyOf(((RegistryToolGroupResolver) resolver).getToolGroups());
	}

}
