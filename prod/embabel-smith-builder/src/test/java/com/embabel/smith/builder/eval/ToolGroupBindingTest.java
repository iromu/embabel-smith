/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.eval;

import com.embabel.agent.core.Action;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentScope;
import com.embabel.agent.core.CoreToolGroups;
import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.core.ToolGroupRequirement;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.samples.SampleAgentSpecs;
import com.embabel.smith.common.validation.AgentSpecValidator;
import com.embabel.smith.common.validation.ValidationResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Falsifier for design decision D9 of
 * {@code openspec/changes/poc27-improves-builder-agentspecs/design.md}: that a pack
 * step's {@code toolGroups} declaration <em>reaches execution</em>. The design's stated
 * evidence is that {@code StepSpecAgentScopeBuilder} and
 * {@code support.PromptedActionSpecAction} reference tool groups inside
 * {@code embabel-agent-spec-1.5.4-20261112.000934-68.jar} — a reference, not a behaviour
 * — and its Risks section asks for "a test that a step declaring a group can use it and a
 * step declaring none cannot".
 *
 * <p>
 * <b>What this establishes offline.</b> A declared group becomes a
 * {@link ToolGroupRequirement} on the compiled action with its name passed through
 * <em>verbatim</em> ({@code PromptedActionSpec.emit} maps each declared string straight
 * into {@code ToolGroupRequirement(role)}). Nothing on this repository's path resolves
 * that name against anything: {@code PromptedActionSpec.emit} never reads the scope's
 * offered-group list, the platform's {@code deploy} is a map insertion plus an event, and
 * {@code AgentSpecValidator} does not inspect {@code toolGroups} at all. So a
 * <em>misspelled</em> group deploys successfully and is carried unchanged — the same
 * family of defect as the unnamed prompt variable that invalidated POC 27's outer loop,
 * where a name resolving to nothing was indistinguishable from a name resolving
 * correctly. The difference is that the prompt-variable case now has a validator check
 * ({@code PROMPT_UNDECLARED_INPUT}) and the tool-group case has none.
 *
 * <p>
 * <b>What it cannot establish offline, and says so.</b> Whether a role that survives to
 * prompt time actually yields tools is decided at call time by the platform's
 * {@code ToolGroupResolver} ({@code PromptedActionSpecAction} hands the requirement set
 * to {@code PromptRunner.withToolGroups}), not by anything here. That half is the
 * {@code live}-tagged nested case below.
 *
 * <p>
 * <b>The platform double.</b> {@link AgentSpecInvokerFactory} needs an
 * {@link AgentPlatform} but the question is about the pack it compiles, so the platform
 * is a mock that records the {@link AgentScope} handed to {@code deploy(AgentScope)}. Two
 * facts make that faithful rather than convenient, both read from the 1.5.4 bytecode:
 * {@code AgentPlatform.deploy(AgentScope)} wraps the scope in an {@code Agent} passing
 * {@code getActions()} through with no filtering, and
 * {@code DefaultAgentPlatform.deploy(Agent)} only stores the agent and fires an
 * {@code AgentDeploymentEvent}. Every class under examination — the scope builder, the
 * spec's {@code emit}, the assembler, the writer — runs unmocked.
 *
 * @author Ivan Rodriguez
 */
class ToolGroupBindingTest {

	private static final String USER_INPUT = SampleAgentSpecs.USER_INPUT;

	private static final String BACKGROUND_MESSAGE = SampleAgentSpecs.BACKGROUND_MESSAGE;

	/**
	 * A typo of {@code CoreToolGroups.WEB}, and not the role of any group upstream
	 * publishes.
	 */
	private static final String MISSPELLED_GROUP = "webb";

	/**
	 * A single-step pack that declares {@code toolGroups} on its only step. The step
	 * interpolates {@code UserInput}, which the validator requires of a step that
	 * declares an input; without it the pack is rejected before the tool-group question
	 * is reached.
	 */
	private static AgentSpec packDeclaring(String... toolGroups) {
		ActionStepSpec step = new ActionStepSpec(ActionStepSpec.STEP_TYPE, "say", "Say the input back",
				List.of(USER_INPUT), BACKGROUND_MESSAGE, "Answer this: {{userInput.content}}", null, null, null, null,
				null, null, List.of(toolGroups));
		GoalStepSpec goal = GoalStepSpec.exportedGoal("done", "A single-step spec", BACKGROUND_MESSAGE);
		return new AgentSpec(new AgentSpecManifest("toolgroup-probe", "1.0.0"), goal, List.of(step), List.of());
	}

	/**
	 * Deploys a pack through the production {@link AgentSpecInvokerFactory} and returns
	 * the actions it compiled — the artifact the platform was handed to deploy.
	 */
	private static List<Action> actionsDeployedBy(AgentSpec pack) {
		AgentPlatform platform = mock(AgentPlatform.class);
		// The factory widens the platform with the pack's types; a double that
		// contributes no types of
		// its own returns its argument, which is the dictionary the real path would have
		// compiled with.
		when(platform.plus(any(DataDictionary.class))).thenAnswer((invocation) -> invocation.getArgument(0));
		List<Action> deployed = new ArrayList<>();
		when(platform.deploy(any(AgentScope.class))).thenAnswer((invocation) -> {
			deployed.addAll(invocation.getArgument(0, AgentScope.class).getActions());
			return platform;
		});
		new AgentSpecInvokerFactory(platform).invokerFor(pack);
		return deployed;
	}

	private static List<String> rolesOf(List<Action> actions) {
		return actions.stream()
			.flatMap((action) -> action.getToolGroups().stream())
			.map(ToolGroupRequirement::getRole)
			.sorted()
			.toList();
	}

	@Test
	void aDeclaredToolGroupReachesTheCompiledActionAsARequirementNamedTheSame() {
		List<Action> actions = actionsDeployedBy(packDeclaring(CoreToolGroups.WEB));

		assertThat(actions).as("the step compiled into one action").hasSize(1);
		assertThat(rolesOf(actions)).containsExactly(CoreToolGroups.WEB);
	}

	@Test
	void aStepThatDeclaresNoToolGroupCompilesToAnActionCarryingNoRequirement() {
		assertThat(rolesOf(actionsDeployedBy(packDeclaring()))).isEmpty();
	}

	/**
	 * The case D9 depends on. A group name that resolves to nothing is accepted by the
	 * deploy and carried verbatim, so nothing on this path can tell a declared group from
	 * a typo: the pack the builder writes and the pack it mis-writes are
	 * indistinguishable at deploy time.
	 */
	@Test
	void aMisspelledToolGroupDeploysAndIsCarriedVerbatimRatherThanRejected() {
		AgentSpec pack = packDeclaring(MISSPELLED_GROUP);
		assertThat(MISSPELLED_GROUP).isNotEqualTo(CoreToolGroups.WEB);

		List<Action> actions = actionsDeployedBy(pack);

		assertThat(actions).as("the misspelling did not stop the pack deploying").hasSize(1);
		assertThat(rolesOf(actions)).as("nothing resolved the name away").containsExactly(MISSPELLED_GROUP);
	}

	@Test
	void theThreeDeploysDifferOnlyInTheRoleStringsTheCompiledActionsCarry() {
		List<Action> declared = actionsDeployedBy(packDeclaring(CoreToolGroups.WEB));
		List<Action> undeclared = actionsDeployedBy(packDeclaring());
		List<Action> misspelled = actionsDeployedBy(packDeclaring(MISSPELLED_GROUP));

		// Same action, same count, same name each time: the declaration is one attribute
		// of the
		// compiled action and changes nothing else about it.
		assertThat(declared).hasSameSizeAs(undeclared).hasSameSizeAs(misspelled);
		assertThat(declared.stream().map(Action::getName).toList())
			.isEqualTo(misspelled.stream().map(Action::getName).toList());

		assertThat(rolesOf(declared)).containsExactly(CoreToolGroups.WEB);
		assertThat(rolesOf(undeclared)).isEmpty();
		assertThat(rolesOf(misspelled)).containsExactly(MISSPELLED_GROUP);
	}

	/**
	 * {@code AgentSpecValidator} checks prompt references and type references but never
	 * looks at {@code toolGroups}, so the typo is clean through validation. The control
	 * case is what makes this an absence rather than an unused check: the same validator
	 * on the same pack shape does reject a dangling <em>type</em> name, so a misspelled
	 * group is not being caught somewhere else in the same pass.
	 */
	@Test
	void agentSpecValidatorAcceptsAMisspelledToolGroupWhileRejectingADanglingType() {
		ValidationResult typo = new AgentSpecValidator().validate(packDeclaring(MISSPELLED_GROUP));

		assertThat(typo.isValid()).as("validator findings on a typo'd group: %s", typo.issues()).isTrue();
		assertThat(typo.issues()).noneMatch((issue) -> String.valueOf(issue.message()).contains(MISSPELLED_GROUP));

		ActionStepSpec danglingType = new ActionStepSpec(ActionStepSpec.STEP_TYPE, "say", "Say the input back",
				List.of(USER_INPUT), "NoSuchType", "Answer this: {{userInput.content}}", null, null, null, null, null,
				null, List.of(MISSPELLED_GROUP));
		AgentSpec suspect = new AgentSpec(new AgentSpecManifest("toolgroup-probe", "1.0.0"),
				GoalStepSpec.exportedGoal("done", "A single-step spec", BACKGROUND_MESSAGE), List.of(danglingType),
				List.of());

		assertThat(new AgentSpecValidator().validate(suspect).issuesWithCode("DANGLING_TYPE_REF"))
			.as("control: the validator does run on this pack shape")
			.isNotEmpty();
	}

	/**
	 * The half the offline path cannot answer: a declared role only yields tools if the
	 * running platform publishes a group with that role, because resolution happens at
	 * prompt time through the platform's {@code ToolGroupResolver}. This is the
	 * precondition D9 silently assumes when it says a pack's agent "can reach
	 * deterministic services"; it needs a booted context, so it is endpoint-gated the way
	 * this module's other context tests are.
	 */
	@Nested
	@SpringBootTest
	@Tag("live")
	@EnabledIfEnvironmentVariable(named = "OPENAI_CUSTOM_BASE_URL", matches = ".+")
	class OnABootedPlatform {

		@Autowired
		AgentPlatform platform;

		@Test
		void thePlatformPublishesTheCoreToolGroupsAStepCanDeclare() {
			List<String> roles = this.platform.getToolGroupResolver()
				.availableToolGroups()
				.stream()
				.map(com.embabel.agent.core.ToolGroupDescription::getRole)
				.toList();

			assertThat(roles).as("groups a builder pack could point at").contains(CoreToolGroups.WEB);
		}

	}

}
