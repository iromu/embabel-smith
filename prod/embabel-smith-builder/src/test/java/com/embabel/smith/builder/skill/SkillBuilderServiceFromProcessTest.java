/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.skill;

import com.embabel.smith.builder.BuildOptions;
import com.embabel.smith.builder.core.BuildTarget;
import com.embabel.smith.builder.core.GenerationTechnique;
import com.embabel.smith.builder.core.SpecPromptCaller;
import com.embabel.smith.builder.publish.PublishTarget;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.common.model.DynamicTypeDef;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.model.ProcessSpec;
import com.embabel.smith.common.model.PropertyDef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline tests for {@code SkillBuilderService.buildFromProcess}: a process is compiled
 * one skill per member agent, so the generator must be asked once per agent and — the
 * part that matters — each of those asks must be a <em>different</em> request. Both
 * assertions are read off the calls the injected {@link SpecPromptCaller} actually
 * received, never off the returned {@link SkillBuildResult}, because the returned object
 * carries one stage's outcome and says nothing about how many stages were run.
 * <p>
 * The distinctness assertion is the discipline POC 27's invalid run turned on: a
 * derivation that collapsed every stage onto the same text would send N byte-identical
 * requests, and no score computed from them could separate two candidates. Comparing the
 * recorded request bytes is what makes that failure impossible to pass.
 * <p>
 * <b>What this test does not prove.</b> It cannot distinguish derivation from delegation.
 * It shows that N requests reached the generator and that they differ; it does not show
 * that the difference was assembled in Java from the agent's step data. An implementation
 * that sent only the agent's name, one that sent the agent's goal description alone, and
 * one that paid for a model call to write each description would all pass both
 * assertions. What it pins is the count and the non-collapse, not the derivation
 * mechanism.
 *
 * @author Ivan Rodriguez
 */
class SkillBuilderServiceFromProcessTest {

	private static final String SKILL_PROMPT = "GEN-SKILL";

	private static final String EVALS_PROMPT = "GEN-EVALS";

	private static final String GRADE_PROMPT = "GRADE";

	private static final String WITH_MARKER = "WITH-ARM";

	private static final String WITH_PROMPT = WITH_MARKER + "\n{skill}";

	private static final String BASE_PROMPT = "BASE-ARM";

	private static final String WITH_ANSWER = "PASS rotate stock by earliest date and record the temperature";

	private static final String BASE_ANSWER = "FAIL stack the pallet and sign the delivery note";

	private static final String EVALS_JSON = """
			{"skill_name": "order-despatch", "evals": [
			  {"id": 1, "prompt": "capture an order", "expected_output": "an order record",
			   "files": [], "expectations": ["must record the line quantities"]},
			  {"id": 2, "prompt": "a limit breach turns up", "expected_output": "an exception raised",
			   "files": [], "expectations": ["must raise the exception"]}
			]}""";

	/**
	 * Every system prompt the build used, in call order, for the failure messages: a
	 * wrong count is only diagnosable if the reader can see what else was called.
	 */
	private final List<String> systems = new ArrayList<>();

	/**
	 * The user messages of the generation calls only — the requests the generator
	 * actually received, which is what both assertions are taken from.
	 */
	private final List<String> generationRequests = new ArrayList<>();

	@Test
	void everyMemberAgentOfTheProcessBuysExactlyOneGenerationCall(@TempDir Path library) {
		ProcessSpec process = process();
		// The count is asserted against the agents, not the steps: the process has three
		// agents and four steps, so an implementation that derived one skill per step
		// cannot satisfy this either.
		assertThat(process.agents()).hasSize(3);
		assertThat(process.totalSteps()).isEqualTo(4);

		assertThat(buildFromProcess(library, process)).isNotNull();

		assertThat(this.generationRequests).as(() -> "systems called: " + this.systems)
			.hasSize(process.agents().size());
	}

	@Test
	void theGenerationRequestsForDistinctStagesAreDistinct(@TempDir Path library) {
		buildFromProcess(library, process());

		// The whole request, so a build that varied only the skill name it addressed —
		// and left the description identical — is caught by the document comparison
		// below, and one that varied nothing at all is caught here.
		assertThat(this.generationRequests).as(() -> "requests sent: " + this.generationRequests)
			.doesNotHaveDuplicates();
		assertThat(this.generationRequests.stream().map(SkillBuilderServiceFromProcessTest::documentOf).toList())
			.doesNotHaveDuplicates();
	}

	/**
	 * The description the generator was handed, lifted out of the {@code <document>}
	 * block {@link SkillGenerator} wraps it in. A request with no such block is returned
	 * whole, so the comparison still fails on an identical request rather than passing
	 * vacuously.
	 * @param request the recorded user message of a generation call
	 * @return the description bytes it carried
	 */
	private static String documentOf(String request) {
		String open = "<document>\n";
		int start = request.indexOf(open);
		if (start < 0) {
			return request;
		}
		int body = start + open.length();
		int end = request.indexOf("\n</document>", body);
		return (end < 0) ? request.substring(body) : request.substring(body, end);
	}

	/**
	 * The three-agent, four-step process: each agent's name, goal and steps are distinct
	 * from the others', which is what gives the derivation something to differentiate on.
	 * Every step interpolates a declared input — a step whose prompt named none of its
	 * inputs is the shape the validator rejects as {@code PROMPT_IGNORES_DECLARED_INPUT},
	 * and it is the shape that made POC 27's run invalid.
	 */
	private static ProcessSpec process() {
		return new ProcessSpec("order-despatch", List.of(intake(), credit(), despatch()));
	}

	private static AgentSpec intake() {
		return agent("intake", "intake_goal", "OrderRecord",
				List.of(ActionStepSpec.action("capture_order", "Capture the customer order from the request",
						List.of("UserInput"), "OrderRecord", "Capture the order from {{userInput.content}}")));
	}

	private static AgentSpec credit() {
		return agent("credit", "credit_goal", "CreditDecision", List.of(
				ActionStepSpec.action("check_limit", "Check the order against the customer's credit limit",
						List.of("UserInput"), "CreditDecision", "Check the limit against {{userInput.content}}"),
				ActionStepSpec.action("flag_exception", "Flag a credit breach for the desk to approve",
						List.of("CreditDecision"), "CreditDecision", "Flag the breach on {{creditDecision.content}}")));
	}

	private static AgentSpec despatch() {
		return agent("despatch", "despatch_goal", "DispatchNote",
				List.of(ActionStepSpec.action("raise_note", "Raise the warehouse despatch note", List.of("UserInput"),
						"DispatchNote", "Raise the despatch note from {{userInput.content}}")));
	}

	/**
	 * An agent declaring the types it references, as the builder's other process fixtures
	 * do, so the aggregate is a well-formed pack directory and not a dangling one.
	 * {@code UserInput} is not declared: it is the built-in entry type, and the
	 * neighbours declare only the types the agent itself produces.
	 */
	private static AgentSpec agent(String name, String goalName, String goalOutput, List<ActionStepSpec> steps) {
		return new AgentSpec(new AgentSpecManifest(name, "1.0.0"),
				GoalStepSpec.exportedGoal(goalName, "the " + name + " deliverable", goalOutput), steps,
				List.of(type(goalOutput)));
	}

	private static DynamicTypeDef type(String name) {
		return new DynamicTypeDef(name, "the " + name + " data",
				List.of(new PropertyDef("content", "string", "the payload")), List.of(), true);
	}

	/**
	 * Runs the process build over the scripted caller and returns its result. The caller
	 * answers every stage the delegated {@code build} pays for — the draft, the suite,
	 * both arms and the judge — with the arms arranged so the lift gate is not what stops
	 * a stage. Overwrite is on because the publish collision gate is not under test here:
	 * {@link com.embabel.smith.builder.core.PackName} truncates a slug at 40 characters,
	 * and a slug collision must not change the call count being measured.
	 */
	private SkillBuildResult buildFromProcess(Path library, ProcessSpec process) {
		SpecPromptCaller model = (system, user) -> {
			this.systems.add(system);
			if (system.startsWith(SKILL_PROMPT)) {
				this.generationRequests.add(user);
				return skillJson();
			}
			if (system.startsWith(EVALS_PROMPT)) {
				return EVALS_JSON;
			}
			if (system.startsWith(WITH_MARKER)) {
				return WITH_ANSWER;
			}
			if (system.startsWith(BASE_PROMPT)) {
				return BASE_ANSWER;
			}
			if (system.startsWith(GRADE_PROMPT)) {
				return judgeAnswer(user.contains("PASS"), countExpectations(user));
			}
			throw new IllegalStateException("unexpected system prompt: " + system);
		};
		SkillBuilderService service = new SkillBuilderService(model, SKILL_PROMPT, EVALS_PROMPT, GRADE_PROMPT,
				WITH_PROMPT, BASE_PROMPT);
		BuildOptions options = new BuildOptions(BuildOptions.DEFAULT_MAX_REFINE, BuildOptions.DEFAULT_SCORE_GATE, true,
				false, GenerationTechnique.SINGLE_SHOT, BuildTarget.SKILL, BuildOptions.DEFAULT_MIN_LIFT);
		return service.buildFromProcess(process, PublishTarget.parse("folder:" + library), options);
	}

	/**
	 * The draft every stage is handed: written the way the skill writing guidelines ask,
	 * so the inspection gate passes on the first draft and each delegated build buys
	 * exactly one generation call. It is the body {@code SkillBuilderServiceTest} proves
	 * is not blocked. Deliberately identical for every stage — the model's answer is not
	 * what is under test, only the request that produced it.
	 */
	private static String skillJson() {
		return "{\"description\": \"Handle an order from capture to despatch.\", \"body\": \""
				+ "# Order despatch\\n\\nCapture an order, clear it for credit and raise the despatch note.\\n\\n"
				+ "## Instructions\\n1. Read the order request the customer sent.\\n"
				+ "2. Check the customer's credit limit before releasing anything.\\n"
				+ "3. Raise the despatch note once credit clears.\\n"
				+ "4. Ask the operator to confirm before closing the order.\\n\\n## Examples\\n"
				+ "- An order over the limit: hold it and flag the breach.\\n"
				+ "- An order inside the limit: release it to the warehouse.\\n\\n## Troubleshooting\\n"
				+ "- A missing credit decision is treated as a hold until the desk answers.\\n\"}";
	}

	private static int countExpectations(String gradingRequest) {
		String tail = gradingRequest.substring(gradingRequest.indexOf("<expectations>"));
		return (int) tail.lines().filter((line) -> line.matches("^\\d+\\. .*")).count();
	}

	private static String judgeAnswer(boolean passed, int count) {
		StringBuilder entries = new StringBuilder();
		for (int i = 0; i < count; i++) {
			entries.append("{\"text\": \"e")
				.append(i)
				.append("\", \"passed\": ")
				.append(passed)
				.append(", \"evidence\": \"evidence\"}");
			if (i < count - 1) {
				entries.append(", ");
			}
		}
		return "{\"expectations\": [" + entries + "]}";
	}

}
