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
import com.embabel.smith.common.model.AgentSpec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What the agentic flow does between a description and a draft, offline. Two things are
 * under test, and neither is a model call: the pack it deploys, and the checks it runs
 * <em>itself</em> on what came back.
 *
 * <p>
 * <b>The checks are the point.</b> A step that is told to validate its own draft may
 * decline — the measured TradingAgents run did exactly that and shipped sixteen dangling
 * type references — so this generator runs the validator and the scanner in Java and
 * sends the findings back to the same pack. The assertions below are therefore about the
 * <em>second request</em>: that it exists, that it carries the findings verbatim, that it
 * is the same task rather than a new one, and that the loop stops.
 *
 * <p>
 * The platform is a mock for one reason:
 * {@link AgenticAgentSpecGenerator#refuse(AgentSpec)} reads the roles it can resolve, and
 * this test needs to say which ones those are — including the case where one of the
 * pack's declarations is missing from the list, which is the case that must stop the
 * build before a call is spent.
 *
 * @author Ivan Rodriguez
 */
class AgenticAgentSpecGeneratorTest {

	/**
	 * The pack {@code cli/BuildCommand} drives under {@code --flow agentic}, addressed
	 * through the class loader as the generator addresses it.
	 */
	private static final Path PACK_DIR = BuilderPackPaths.directory("builder-agentic-blueprint");

	/** The goal {@code builder-agentic-blueprint/goal.yml} exports. */
	private static final String GOAL = "agentSpecAuthoredFromBlueprint";

	/** The groups the shipped pack's two steps declare between them. */
	private static final List<String> PACK_GROUPS = List.of("blueprint", "library", "validate", "security");

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
	 * The measured failure: the goal refers to a type the draft never declares, which is
	 * the {@code DANGLING_TYPE_REF} the pack flow shipped sixteen of on one blueprint
	 * input while its step declined the {@code validatePack} call it was offered.
	 * Declaring the type would make the draft clean, so the finding has to be the missing
	 * declaration.
	 */
	private static final String DANGLING_TYPE_SPEC = """
			{
			  "manifest": { "name": "drafted-pack", "version": "1.0.0" },
			  "goal": {
			    "stepType": "goal", "name": "answered", "outputTypeName": "NeverDeclared",
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

	private static AgentPlatform platformOffering(List<String> roles) {
		// A real registry rather than a mock of availableToolGroups(), because
		// ToolGroupMetadata's factories are Kotlin companions Java cannot name. The
		// groups are the production recipe with no tools in them, which is all a
		// role-resolution check reads.
		var resolver = new RegistryToolGroupResolver("agentic-flow-test",
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
	void aCleanDraftEndsTheFlowAfterOneCall() {
		ScriptedInvokers invokers = new ScriptedInvokers(CLEAN_SPEC);

		CompiledDraft draft = new AgenticAgentSpecGenerator(invokers, platformOffering(PACK_GROUPS), PACK_DIR)
			.generate("Ship orders end to end");

		assertThat(invokers.packs).as("the pack it deployed").containsExactly("builder-agentic-blueprint");
		assertThat(invokers.inputs).as("no re-invocation was needed").containsExactly("Ship orders end to end");
		assertThat(draft.pack().name()).isEqualTo("drafted-pack");
		assertThat(draft.issues()).isEmpty();
	}

	/**
	 * The forcing loop, which is the reason this class exists: the draft is checked in
	 * Java, and the second request is the same task with the findings under it. The
	 * re-asked request must still contain the original description — a re-invocation that
	 * sent only the findings would be a different task, and the step would have nothing
	 * to draft from.
	 */
	@Test
	void aDraftWithFindingsIsSentBackToTheSamePackWithTheFindingsAppended() {
		ScriptedInvokers invokers = new ScriptedInvokers(DANGLING_TYPE_SPEC, CLEAN_SPEC);

		CompiledDraft draft = new AgenticAgentSpecGenerator(invokers, platformOffering(PACK_GROUPS), PACK_DIR)
			.generate("Ship orders end to end");

		assertThat(invokers.inputs).as("the pack asked twice, and never a Java generator").hasSize(2);
		assertThat(invokers.inputs.get(1)).startsWith("Ship orders end to end")
			.contains("checked by the build, in Java")
			.contains("NeverDeclared");
		assertThat(draft.issues()).as("the second draft was clean").isEmpty();
	}

	/**
	 * The ceiling. Findings the model will not fix must stop spending calls rather than
	 * loop until the endpoint does, and the draft that remains is handed on with its
	 * findings recorded — the later gates then see what the Java flow's gates see.
	 */
	@Test
	void theReaskingIsBoundedAndTheLastDraftCarriesWhatStillFailed() {
		ScriptedInvokers invokers = new ScriptedInvokers(DANGLING_TYPE_SPEC);

		CompiledDraft draft = new AgenticAgentSpecGenerator(invokers, platformOffering(PACK_GROUPS), PACK_DIR)
			.generate("Ship orders end to end");

		assertThat(invokers.inputs).as("one call plus the allowed re-invocations")
			.hasSize(AgenticAgentSpecGenerator.MAX_FORCING_ROUNDS + 1);
		assertThat(draft.hasIssues()).as("the findings are recorded, not swallowed").isTrue();
		assertThat(draft.issues()).anyMatch((issue) -> issue.contains("NeverDeclared"));
	}

	/**
	 * The deploy-time refusal. A group that resolves to nothing deploys today, runs, and
	 * reaches nothing — the tier even moves as though the declaration meant something. So
	 * the agentic deploy refuses before any call, and the control half is the test above:
	 * the same pack with the same steps and a complete role list runs.
	 */
	@Test
	void aDeclaredGroupThePlatformCannotResolveRefusesTheBuildBeforeAnyCall() {
		ScriptedInvokers invokers = new ScriptedInvokers(CLEAN_SPEC);

		assertThatThrownBy(
				() -> new AgenticAgentSpecGenerator(invokers, platformOffering(List.of("validate")), PACK_DIR)
					.generate("a description"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("agentic flow")
			.hasMessageContaining("blueprint");

		assertThat(invokers.packs).as("nothing was deployed").isEmpty();
		assertThat(invokers.inputs).as("and nothing was spent").isEmpty();
	}

	@Test
	void aGoalThatProducedNothingFailsNamingTheFlowAndTheGoal() {
		ScriptedInvokers invokers = new ScriptedInvokers((String) null);

		assertThatThrownBy(() -> new AgenticAgentSpecGenerator(invokers, platformOffering(PACK_GROUPS), PACK_DIR)
			.generate("a description")).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("agentic flow")
			.hasMessageContaining(GOAL);
	}

	@Test
	void theFindingsAreAppendedAsAListRatherThanAsProse() {
		String request = AgenticAgentSpecGenerator.withFindings("The task.",
				List.of("validator CODE: first", "second"));

		assertThat(request).startsWith("The task.").contains("- validator CODE: first").contains("- second");
	}

	@Test
	void thePackItDrivesIsTheOneOnDisk() throws IOException {
		AgentSpec pack = new AgentSpecReader().read(PACK_DIR);

		assertThat(pack.name()).isEqualTo("builder-agentic-blueprint");
		assertThat(pack.goal().name()).isEqualTo(GOAL);
	}

}
