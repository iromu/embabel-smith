/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.ToolGroup;
import com.embabel.agent.core.ToolGroupDescription;
import com.embabel.smith.builder.eval.BuilderPackTools;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code validate} tool group that makes a builder pack's
 * {@code toolGroups: [validate]} declaration true (task 4.1b).
 *
 * <p>
 * <b>What each assertion pins.</b> The role, because resolution is by role and the name
 * is the one the pack files already carry — the shipped pack's own step file is read
 * here, so the bean cannot drift from the declaration it exists to honour. Then the
 * group's single member and its schema, because what a group carries is all a model is
 * offered: one tool, named {@link BuilderPackTools#VALIDATE_PACK}, whose argument the
 * schema names {@link BuilderPackTools#SPEC_JSON} rather than javac's {@code arg0}. That
 * last half matters even though the role now resolves: the argument name is the key the
 * call binds, so a group holding a tool whose argument reads {@code arg0} would resolve
 * the role and still mis-call the validator.
 *
 * <p>
 * <b>Why the context case is offline-safe.</b> The nested case boots the application and
 * asks the platform's resolver which groups exist. {@code availableToolGroups()} reads
 * the registry the context filled from its {@code ToolGroup} beans — no model, no
 * endpoint, no network — so it carries neither {@code @Tag("live")} nor the
 * {@code OPENAI_CUSTOM_BASE_URL} gate that {@code eval/ToolGroupBindingTest}'s booted
 * case needs. Booting without an endpoint is what the module's {@code application.yml}
 * exists to allow: every provider is fed an inert-but-well-formed key and URL, so the app
 * starts with nothing configured. Whether a model then <em>calls</em> the tool is a
 * different number and stays with {@code eval/DeployedBuilderPackCallsValidatePackTest}.
 *
 * @author Ivan Rodriguez
 */
class ValidationToolGroupTest {

	/** The shipped pack's step, whose {@code toolGroups} block this bean makes true. */
	private static final Path SHIPPED_STEP = Path.of("builder-agent-specs", "builder-spec-one-shot", "steps",
			"draftAgentSpec.yml");

	private static ToolGroup group() {
		return new ValidationToolGroup().validateToolGroup();
	}

	@Test
	void theGroupIsRegisteredUnderTheRoleTheBuilderPacksDeclare() {
		assertThat(group().getMetadata().getRole())
			.as("the role a step's toolGroups entry resolves against, and the constant the bean states")
			.isEqualTo(ValidationToolGroup.ROLE)
			.isEqualTo("validate");
	}

	/**
	 * The coupling the bean exists for: the registered role is a name some pack really
	 * declares, not a name invented here. Matched as a {@code toolGroups} list entry
	 * rather than as the word anywhere in the file, because the step prompt discusses
	 * validating in prose and prose would make this pass without the declaration.
	 * @throws IOException when the pack is not where the other pack tests read it
	 */
	@Test
	void theRoleIsANameTheShippedPackDeclaresAsAToolGroup() throws IOException {
		String step = Files.readString(SHIPPED_STEP);

		assertThat(step).as("step file %s", SHIPPED_STEP)
			.containsPattern("toolGroups:\\s*\\n\\s*-\\s*" + ValidationToolGroup.ROLE + "\\b");
	}

	@Test
	void theGroupCarriesExactlyOneToolNamedAfterTheValidatorMethodTheModelCalls() {
		List<Tool> tools = group().getTools();

		assertThat(tools).as("the validator alone").hasSize(1);
		assertThat(tools.get(0).getDefinition().getName()).as("tool name offered to a model")
			.isEqualTo(BuilderPackTools.VALIDATE_PACK);
	}

	@Test
	void theToolTheGroupCarriesNamesItsArgumentAsTheAdapterDeclaresItRatherThanAsJavacDoes() {
		Tool tool = group().getTools().get(0);
		List<Tool.Parameter> parameters = tool.getDefinition().getInputSchema().getParameters();

		assertThat(parameters).as("one argument, as the adapter declares").hasSize(1);
		assertThat(parameters.get(0).getName()).isEqualTo(BuilderPackTools.SPEC_JSON);
		assertThat(tool.getDefinition().getInputSchema().toJsonSchema()).contains(BuilderPackTools.SPEC_JSON)
			.doesNotContain("arg0");
	}

	/**
	 * The resolver the platform actually consults at prompt time, whose registry is the
	 * {@code ToolGroup} beans of the context that booted it. Offline for the reason in
	 * the class javadoc: this reads a registry, it does not call a model.
	 */
	@Nested
	@SpringBootTest
	class OnABootedPlatform {

		@Autowired
		AgentPlatform platform;

		@Test
		void theBootedPlatformPublishesTheValidateGroupAStepCanDeclare() {
			List<String> roles = this.platform.getToolGroupResolver()
				.availableToolGroups()
				.stream()
				.map(ToolGroupDescription::getRole)
				.toList();

			assertThat(roles).as("roles the platform can resolve a toolGroups entry to")
				.contains(ValidationToolGroup.ROLE);
		}

	}

}
