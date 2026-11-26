/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.tools;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.ToolGroup;
import com.embabel.agent.core.ToolGroupDescription;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.common.model.AgentsPack;
import com.embabel.smith.common.samples.SampleAgentSpecs;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code process} group, and the re-shape it exists to expose: the cross-agent checks
 * take the process <em>document</em> now.
 *
 * <p>
 * The argument used to be a directory. That is the shape that cost a measured 116 tool
 * calls against paths a drafting step had invented, because during a drafting step there
 * is no process on disk to point at — the thing to check is the model's own pending
 * answer. So the case that a directory is no longer accepted is asserted here on purpose:
 * it is the re-shape, not an accident of the parser.
 *
 * @author Ivan Rodriguez
 */
class AgentsPackValidationToolGroupTest {

	private final ObjectMapper json = new ObjectMapper();

	@TempDir
	Path workDir;

	private static ToolGroup group() {
		return group(new ToolSurface());
	}

	private static ToolGroup group(ToolSurface surface) {
		return new AgentsPackValidationToolGroup().processChecks(surface);
	}

	/**
	 * The third of the deterministic checks, routed through the same memo: an identical
	 * repeat of the call is answered from the first one. The group is registered and no
	 * shipped step declares it yet, so this is the only place the claim is held.
	 */
	@Test
	void anIdenticalRepeatOfTheProcessCallIsAnsweredFromTheFirstOne() {
		ToolSurface surface = new ToolSurface();
		String arguments = "{\"processJson\": \"\"}";
		Tool tool = group(surface).getTools().get(0);

		String first = ((Tool.Result.Text) tool.call(arguments)).getContent();
		String repeat = ((Tool.Result.Text) tool.call(arguments)).getContent();

		assertThat(first).as("the first call runs the body")
			.contains(AgentsPackValidationToolGroup.VALIDATE_PROCESS)
			.doesNotContain("identical to an earlier call");
		assertThat(repeat).startsWith("[identical to an earlier call in this run");
	}

	@Test
	void theGroupIsRegisteredUnderTheRoleAProcessStepDeclares() {
		assertThat(group().getMetadata().getRole())
			.as("the role a step's toolGroups entry resolves against, and the constant the bean states")
			.isEqualTo(AgentsPackValidationToolGroup.ROLE)
			.isEqualTo("process");
	}

	@Test
	void theGroupCarriesTheProcessValidatorAndNamesItsArgument() {
		List<Tool> tools = group().getTools();

		assertThat(tools).as("the process validator alone").hasSize(1);
		assertThat(tools.get(0).getDefinition().getName()).isEqualTo(AgentsPackValidationToolGroup.VALIDATE_PROCESS);
		assertThat(tools.get(0).getDefinition().getInputSchema().getParameters()).extracting(Tool.Parameter::getName)
			.containsExactly(AgentsPackValidationToolGroup.PROCESS_JSON);
		assertThat(tools.get(0).getDefinition().getInputSchema().toJsonSchema()).doesNotContain("arg0");
	}

	@Test
	void aSoundProcessIsReportedAsSound() throws Exception {
		String document = this.json.writeValueAsString(AgentsPack.single("solo", SampleAgentSpecs.explainer()));

		assertThat(AgentsPackValidationToolGroup.findings(document)).contains("No findings: the process is sound")
			.contains("cycle between agents: false");
	}

	/**
	 * The finding the check exists for: two agents exporting one goal name. Reported
	 * through the tool in the validator's own words, which is what makes the pass-through
	 * claim testable.
	 */
	@Test
	void aGoalCollisionInTheDocumentIsReportedInTheValidatorsOwnWords() throws Exception {
		AgentSpec first = SampleAgentSpecs.explainer();
		AgentSpec second = new AgentSpec(new AgentSpecManifest("repeater", "1.0.0"), first.goal(), first.steps(),
				first.types());
		String document = this.json.writeValueAsString(new AgentsPack("colliding", List.of(first, second)));

		assertThat(AgentsPackValidationToolGroup.findings(document)).contains("Goal name").contains("repeater");
	}

	@Test
	void aDirectoryIsNoLongerWhatThisToolTakes() {
		assertThat(AgentsPackValidationToolGroup.findings(this.workDir.toString()))
			.as("the re-shape: a path is not a process document, and saying so is the point")
			.contains("could not read that as a process document");
	}

	@Test
	void aCallWithNoArgumentSaysWhatItNeeded() {
		assertThat(AgentsPackValidationToolGroup.findings("")).contains(AgentsPackValidationToolGroup.PROCESS_JSON)
			.contains("needs the");
	}

	@Test
	void theAdapterReportsTheSameFindingsTheServiceReportsOnTheSameDocument() throws IOException {
		AgentSpec first = SampleAgentSpecs.explainer();
		AgentsPack agentsPack = new AgentsPack("colliding", List.of(first, first));

		assertThat(new AgentsPackValidationTool().validateProcess(this.json.writeValueAsString(agentsPack)).issues())
			.isEqualTo(new com.embabel.smith.common.validation.AgentsPackValidator().validate(agentsPack).issues())
			.isNotEmpty();
	}

	@Nested
	@SpringBootTest
	class OnABootedPlatform {

		@Autowired
		AgentPlatform platform;

		@Test
		void theBootedPlatformPublishesTheProcessGroupAStepCanDeclare() {
			List<String> roles = this.platform.getToolGroupResolver()
				.availableToolGroups()
				.stream()
				.map(ToolGroupDescription::getRole)
				.toList();

			assertThat(roles).as("roles the platform can resolve a toolGroups entry to")
				.contains(AgentsPackValidationToolGroup.ROLE);
		}

	}

}
