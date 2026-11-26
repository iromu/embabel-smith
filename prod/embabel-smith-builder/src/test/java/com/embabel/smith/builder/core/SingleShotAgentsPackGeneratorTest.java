/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentsPack;
import com.embabel.smith.common.validation.AgentsPackValidationResult;
import com.embabel.smith.common.validation.AgentsPackValidator;
import org.junit.jupiter.api.Test;

/**
 * Offline tests for the multi-agent generation step: a canned model answer is injected
 * through the {@link SpecPromptCaller} seam and checked as a parsed, ordered, normalised
 * process — no model and no endpoint needed.
 *
 * @author Ivan Rodriguez
 */
class SingleShotAgentsPackGeneratorTest {

	private static final String MULTI_JSON = """
			{
			  "process": "trading",
			  "agents": [
			    {
			      "manifest": { "name": "research", "version": "1.0.0" },
			      "goal": { "stepType": "goal", "name": "research_goal", "description": "research done",
			                "outputTypeName": "MarketReport", "export": true },
			      "steps": [
			        { "stepType": "action", "name": "analyze_market", "description": "a",
			          "inputTypeNames": ["UserInput"], "outputTypeName": "MarketReport",
			          "prompt": "Analyse {{ user_input }}" }
			      ],
			      "types": [ { "name": "MarketReport", "description": "mr" } ]
			    },
			    {
			      "manifest": { "name": "decision", "version": "1.0.0" },
			      "goal": { "stepType": "goal", "name": "decision_goal", "description": "decision done",
			                "outputTypeName": "FinalDecision", "export": true },
			      "steps": [
			        { "stepType": "action", "name": "decide", "description": "d",
			          "inputTypeNames": ["MarketReport"], "outputTypeName": "FinalDecision",
			          "prompt": "Decide on {{ marketReport }}" }
			      ],
			      "types": [ { "name": "FinalDecision", "description": "fd" } ]
			    }
			  ]
			}
			""";

	private final AgentsPackValidator validator = new AgentsPackValidator();

	@Test
	void theDescriptionArrivesInsideDocumentTags() {
		String[] seen = new String[1];
		SingleShotAgentsPackGenerator generator = new SingleShotAgentsPackGenerator((system, user) -> {
			seen[0] = user;
			return MULTI_JSON;
		}, "the multi-agent prompt");

		generator.generateAgentsPack("run the research debate and decide");

		assertThat(seen[0]).contains("<document>\nrun the research debate and decide\n</document>");
	}

	@Test
	void parsesTheAnswerIntoAnOrderedListOfPacks() {
		AgentsPackDraft draft = generator(MULTI_JSON).generateAgentsPack("a business process");

		AgentsPack agentsPack = draft.process();
		assertThat(draft.processName()).isEqualTo("trading");
		assertThat(draft.issues()).isEmpty();
		assertThat(agentsPack.process()).isEqualTo("trading");
		assertThat(agentsPack.agents()).extracting(AgentSpec::name).containsExactly("research", "decision");
		assertThat(agentsPack.totalSteps()).isEqualTo(2);
		assertThat(agentsPack.finalOutputType()).isEqualTo("FinalDecision");
	}

	@Test
	void theParsedProcessValidatesItsCrossAgentHandoffs() {
		AgentsPack agentsPack = generator(MULTI_JSON).generateAgentsPack("a business process").process();

		AgentsPackValidationResult result = this.validator.validate(agentsPack);

		assertThat(result.isValid()).as(() -> result.issues().toString()).isTrue();
		assertThat(result.sharedTypes()).isEqualTo(1);
	}

	@Test
	void everyAgentIsNormalisedTheWayASinglePackIs() {
		AgentsPack agentsPack = generator(MULTI_JSON).generateAgentsPack("a business process").process();

		AgentSpec research = agentsPack.agents().get(0);
		assertThat(research.manifest().version()).isEqualTo("1.0.0");
		assertThat(research.goal().isExported()).isTrue();
		// The model wrote the prompt var in snake_case with spaces; the runtime binds the
		// decapitalized short name of the declared input.
		assertThat(research.steps().get(0).prompt()).contains("{{userInput}}").doesNotContain("user_input");
	}

	@Test
	void theProcessNameFallsBackToTheFirstAgentWhenTheModelOmitsIt() {
		String answer = MULTI_JSON.replace("\"process\": \"trading\",", "");

		AgentsPackDraft draft = generator(answer).generateAgentsPack("a business process");

		assertThat(draft.processName()).isEqualTo("research");
	}

	@Test
	void markdownFencesAroundTheAnswerAreStripped() {
		AgentsPackDraft draft = generator("Sure thing:\n```json\n" + MULTI_JSON + "\n```\nHope that helps.")
			.generateAgentsPack("a business process");

		assertThat(draft.process().agents()).hasSize(2);
	}

	@Test
	void anAnswerThatNamesNoAgentsIsAFailureRatherThanAnEmptyProcess() {
		String answer = "{ \"process\": \"empty\", \"agents\": [] }";

		assertThatThrownBy(() -> generator(answer).generateAgentsPack("anything"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("no agents");
	}

	@Test
	void anAnswerThatNamesASingleAgentIsAFailureRatherThanADegenerateProcess() {
		// One pack is the single-agent candidate, not an agents-pack. Accepting it would
		// let the build publish a lone pack labelled "agents-pack".
		String answer = """
				{
				  "process": "solo",
				  "agents": [
				    {
				      "manifest": { "name": "explainer", "version": "1.0.0" },
				      "goal": { "stepType": "goal", "name": "explain_goal", "description": "done",
				                   "outputTypeName": "BackgroundMessage", "export": true },
				      "steps": [
				        { "stepType": "action", "name": "say", "description": "s",
				         "inputTypeNames": ["UserInput"], "outputTypeName": "BackgroundMessage",
				         "prompt": "Repeat {{userInput}}" }
				      ],
				      "types": []
				    }
				  ]
				}
				""";

		assertThatThrownBy(() -> generator(answer).generateAgentsPack("anything"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("single agent");
	}

	@Test
	void anAnswerWithNoJsonAtAllIsAFailure() {
		assertThatThrownBy(() -> generator("the model apologised").generateAgentsPack("anything"))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void theBundledPromptAsksForTheProcessSchemaTheParserExpects() {
		String prompt = SingleShotAgentsPackGenerator.defaultPrompt();

		assertThat(prompt).contains("MULTI-AGENT").contains("\"agents\"").contains("outputTypeName");
	}

	private static SingleShotAgentsPackGenerator generator(String answer) {
		return new SingleShotAgentsPackGenerator((system, user) -> answer, "the multi-agent prompt");
	}

}
