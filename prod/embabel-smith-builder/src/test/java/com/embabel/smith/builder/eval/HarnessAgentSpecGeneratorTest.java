/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.eval;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.spi.support.RegistryToolGroupResolver;
import com.embabel.smith.builder.core.BuilderPackPaths;
import com.embabel.smith.builder.core.CompiledDraft;
import com.embabel.smith.builder.tools.ToolGroups;
import com.embabel.smith.common.io.AgentSpecReader;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What the harness flow does between a description and a draft, offline: the pack it
 * deploys, the checks it runs itself on what came back, and the reach of the reading
 * tools its steps are built around. No model is called — every answer is supplied.
 *
 * <p>
 * <b>The claims here.</b> The pack is the harness pack, and its three steps are survey,
 * plan, draft; a draft comes back checked whether or not the step called the validator it
 * was offered, because the scripted invoker below never calls a tool and the findings
 * still arrive; and a pack that declares a role this context cannot resolve is refused
 * before a call is spent. The cursor's own reach — what a step may read, and what a
 * repeat costs — is {@code tools/HarnessDocumentCursorTest}. The forcing loop is
 * {@link AgenticAgentSpecGenerator}'s, shared rather than copied, so its ceiling is the
 * same constant and the assertion here is that the harness reaches it and stops.
 *
 * <p>
 * The platform is a mock for one reason:
 * {@link AgenticAgentSpecGenerator#refuse(AgentSpec)} reads the roles the context
 * resolves, and the case that matters is the pack declaring one that is missing from that
 * list.
 *
 * @author Ivan Rodriguez
 */
class HarnessAgentSpecGeneratorTest {

	/**
	 * The pack {@code cli/BuildCommand} drives under {@code --flow harness}, addressed
	 * through the class loader as {@link HarnessAgentSpecGenerator#builderPack()}
	 * addresses it.
	 */
	private static final Path PACK_DIR = BuilderPackPaths.directory("builder-harness");

	/** The goal {@code builder-harness/goal.yml} exports. */
	private static final String GOAL = "agentSpecAuthoredFromDocumentRoots";

	/** The groups the shipped pack's three steps declare between them. */
	private static final List<String> PACK_ROLES = List.of("blueprint", "library", "validate", "security");

	/**
	 * A draft the checks pass: goal reachable, every type produced, one prompt variable
	 * bound.
	 */
	private static final String CLEAN_SPEC = """
			{
			  "manifest": { "name": "drafted-pack", "version": "1.0.0" },
			  "goal": {
			    "stepType": "goal", "name": "answered", "outputTypeName": "Answer",
			    "description": "The answer was produced", "export": true
			  },
			  "steps": [
			    {
			      "stepType": "action", "name": "answer", "inputTypeNames": ["UserInput"],
			      "outputTypeName": "Answer", "description": "Answers",
			      "prompt": "Answer this: {{userInput.content}}", "toolGroups": []
			    }
			  ],
			  "types": [
			    { "name": "Answer", "description": "The answer", "ownProperties": [
			      { "name": "text", "type": "string", "description": "The text" }], "parents": [] }
			  ]
			}""";

	/**
	 * The failure the forcing loop exists for: the goal names a type the draft never
	 * declares, which is what the pack flow shipped sixteen times on one blueprint input
	 * while its step declined the {@code validatePack} call it was offered.
	 */
	private static final String DANGLING_TYPE_SPEC = CLEAN_SPEC.replace(
			"\"name\": \"answered\", \"outputTypeName\": \"Answer\"",
			"\"name\": \"answered\", \"outputTypeName\": \"NeverDeclared\"");

	private static AgentPlatform platformOffering(List<String> roles) {
		// A real registry rather than a mock of availableToolGroups(), because
		// ToolGroupMetadata's factories are Kotlin companions Java cannot name. The
		// groups
		// are the production recipe with no tools in them, which is all a role check
		// reads.
		var resolver = new RegistryToolGroupResolver("harness-flow-test",
				roles.stream().map((role) -> ToolGroups.ofTools(role, "The " + role + " group", List.of())).toList());
		AgentPlatform platform = mock(AgentPlatform.class);
		when(platform.getToolGroupResolver()).thenReturn(resolver);
		return platform;
	}

	/** Deploys nothing and answers with the texts in order, repeating the last one. */
	private static final class ScriptedInvokers implements InvokerFactory {

		private final List<String> packs = new ArrayList<>();

		private final List<String> inputs = new ArrayList<>();

		private final List<String> answers;

		private ScriptedInvokers(String... answers) {
			this.answers = Arrays.asList(answers);
		}

		@Override
		public AgentSpecInvoker invokerFor(AgentSpec pack) {
			return invokerFor(pack, null);
		}

		@Override
		public AgentSpecInvoker invokerFor(AgentSpec pack, String model) {
			this.packs.add(pack.name());
			return (input) -> {
				String answer = this.answers.get(Math.min(this.inputs.size(), this.answers.size() - 1));
				this.inputs.add(input);
				return answer;
			};
		}

	}

	@Test
	void thePackItDrivesIsTheHarnessPackOnDisk() throws IOException {
		AgentSpec pack = new AgentSpecReader().read(PACK_DIR);

		assertThat(pack.name()).isEqualTo("builder-harness");
		assertThat(pack.goal().name()).isEqualTo(GOAL);
		assertThat(pack.steps().stream().map(ActionStepSpec::name).toList())
			.as("survey, then decide what to read next, then draft — the run order is the planner's, not the reader's")
			.containsExactlyInAnyOrder("surveyDocumentRoots", "planWhatToReadNext", "draftAgentSpecFromRoots");
	}

	@Test
	void aCleanDraftEndsTheFlowAfterOneCall() {
		ScriptedInvokers invokers = new ScriptedInvokers(CLEAN_SPEC);

		CompiledDraft draft = new HarnessAgentSpecGenerator(invokers, platformOffering(PACK_ROLES))
			.generate("Ship orders end to end");

		assertThat(invokers.packs).as("the pack it deployed").containsExactly("builder-harness");
		assertThat(invokers.inputs).as("no re-invocation was needed").containsExactly("Ship orders end to end");
		assertThat(draft.pack().name()).isEqualTo("drafted-pack");
		assertThat(draft.issues()).isEmpty();
	}

	/**
	 * The forced check, which is the whole reason the flow is not "the pack, and trust
	 * it": the scripted answer stands in for a step that never called
	 * {@code validatePack} — a tool call is not scripted here because the point is the
	 * decline — and the draft is still checked in Java and sent back with the findings
	 * under the same request.
	 */
	@Test
	void aDeclinedValidatorCallDoesNotSkipTheCheck() {
		ScriptedInvokers invokers = new ScriptedInvokers(DANGLING_TYPE_SPEC, CLEAN_SPEC);

		CompiledDraft draft = new HarnessAgentSpecGenerator(invokers, platformOffering(PACK_ROLES))
			.generate("Ship orders end to end");

		assertThat(invokers.inputs).as("the pack asked twice, and never a Java generator").hasSize(2);
		assertThat(invokers.inputs.get(1)).startsWith("Ship orders end to end")
			.contains("checked by the build, in Java")
			.contains("NeverDeclared");
		assertThat(draft.issues()).as("the second draft was clean").isEmpty();
	}

	/**
	 * The ceiling, and what happens at it: findings the model will not fix stop spending
	 * calls, and the draft that remains is handed on carrying them rather than being
	 * published as if it had passed. The later gates are the same gates the Java flow's,
	 * so what they see is the unfixed failure.
	 */
	@Test
	void theSecondUnfixedFailureIsRecordedRatherThanPublished() {
		ScriptedInvokers invokers = new ScriptedInvokers(DANGLING_TYPE_SPEC);

		CompiledDraft draft = new HarnessAgentSpecGenerator(invokers, platformOffering(PACK_ROLES))
			.generate("Ship orders end to end");

		assertThat(invokers.inputs).as("one call plus the allowed re-invocations, and no more")
			.hasSize(AgenticAgentSpecGenerator.MAX_FORCING_ROUNDS + 1);
		assertThat(draft.hasIssues()).as("the findings are recorded, not swallowed").isTrue();
		assertThat(draft.issues()).anyMatch((issue) -> issue.contains("NeverDeclared"));
	}

	/**
	 * The deploy-time refusal, and the control half is the test above: the same pack with
	 * a complete role list runs. A role that resolves to nothing would deploy, run, and
	 * reach nothing, with the tier moving as though the declaration meant something.
	 */
	@Test
	void aDeclaredRoleTheContextCannotResolveRefusesTheBuildBeforeAnyCall() {
		ScriptedInvokers invokers = new ScriptedInvokers(CLEAN_SPEC);

		assertThatThrownBy(() -> new HarnessAgentSpecGenerator(invokers, platformOffering(List.of("validate")))
			.generate("a description")).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("harness flow")
			.hasMessageContaining("builder-harness")
			.hasMessageContaining("blueprint");

		assertThat(invokers.packs).as("nothing was deployed").isEmpty();
		assertThat(invokers.inputs).as("and nothing was spent").isEmpty();
	}

	@Test
	void aGoalThatProducedNothingFailsNamingTheFlowAndTheGoal() {
		ScriptedInvokers invokers = new ScriptedInvokers((String) null);

		assertThatThrownBy(
				() -> new HarnessAgentSpecGenerator(invokers, platformOffering(PACK_ROLES)).generate("a description"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("harness flow")
			.hasMessageContaining(GOAL);
	}

	/**
	 * The cursor's reach and cost are asserted where the cursor lives, in
	 * {@code tools/HarnessDocumentCursorTest}: that a step cannot read outside the roots
	 * the driver opened, and that an identical repeat of a read is served from the first.
	 * What is under test here is the driver: the pack it deploys, the checks it runs
	 * itself, and the refusals it raises in its own name.
	 */

}
