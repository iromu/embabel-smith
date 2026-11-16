/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.ProcessSpec;
import com.embabel.smith.common.validation.ProcessValidationResult;
import com.embabel.smith.common.validation.ProcessValidator;
import org.junit.jupiter.api.Test;

/**
 * Offline tests for the multi-agent generation step: a canned model answer is injected
 * through the {@link SpecPromptCaller} seam and checked as a parsed, ordered, normalised
 * process — no model and no endpoint needed.
 *
 * @author Ivan Rodriguez
 */
class SingleShotProcessSpecGeneratorTest {

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

	private final ProcessValidator validator = new ProcessValidator();

	@Test
	void parsesTheAnswerIntoAnOrderedListOfPacks() {
		ProcessDraft draft = generator(MULTI_JSON).generateProcess("a business process");

		ProcessSpec process = draft.process();
		assertThat(draft.processName()).isEqualTo("trading");
		assertThat(draft.issues()).isEmpty();
		assertThat(process.process()).isEqualTo("trading");
		assertThat(process.agents()).extracting(AgentSpec::name).containsExactly("research", "decision");
		assertThat(process.totalSteps()).isEqualTo(2);
		assertThat(process.finalOutputType()).isEqualTo("FinalDecision");
	}

	@Test
	void theParsedProcessValidatesItsCrossAgentHandoffs() {
		ProcessSpec process = generator(MULTI_JSON).generateProcess("a business process").process();

		ProcessValidationResult result = this.validator.validate(process);

		assertThat(result.isValid()).as(() -> result.issues().toString()).isTrue();
		assertThat(result.sharedTypes()).isEqualTo(1);
	}

	@Test
	void everyAgentIsNormalisedTheWayASinglePackIs() {
		ProcessSpec process = generator(MULTI_JSON).generateProcess("a business process").process();

		AgentSpec research = process.agents().get(0);
		assertThat(research.manifest().version()).isEqualTo("1.0.0");
		assertThat(research.goal().isExported()).isTrue();
		// The model wrote the prompt var in snake_case with spaces; the runtime binds the
		// decapitalized short name of the declared input.
		assertThat(research.steps().get(0).prompt()).contains("{{userInput}}").doesNotContain("user_input");
	}

	@Test
	void theProcessNameFallsBackToTheFirstAgentWhenTheModelOmitsIt() {
		String answer = MULTI_JSON.replace("\"process\": \"trading\",", "");

		ProcessDraft draft = generator(answer).generateProcess("a business process");

		assertThat(draft.processName()).isEqualTo("research");
	}

	@Test
	void markdownFencesAroundTheAnswerAreStripped() {
		ProcessDraft draft = generator("Sure thing:\n```json\n" + MULTI_JSON + "\n```\nHope that helps.")
			.generateProcess("a business process");

		assertThat(draft.process().agents()).hasSize(2);
	}

	@Test
	void anAnswerThatNamesNoAgentsIsAFailureRatherThanAnEmptyProcess() {
		String answer = "{ \"process\": \"empty\", \"agents\": [] }";

		assertThatThrownBy(() -> generator(answer).generateProcess("anything"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("no agents");
	}

	@Test
	void anAnswerThatNamesASingleAgentIsAFailureRatherThanADegenerateProcess() {
		// One pack is the single-agent candidate, not a multi-agent process. Accepting it
		// would let the build publish a lone pack labelled "multi-agent process".
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

		assertThatThrownBy(() -> generator(answer).generateProcess("anything"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("single agent");
	}

	@Test
	void anAnswerWithNoJsonAtAllIsAFailure() {
		assertThatThrownBy(() -> generator("the model apologised").generateProcess("anything"))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void theBundledPromptAsksForTheProcessSchemaTheParserExpects() {
		String prompt = SingleShotProcessSpecGenerator.defaultPrompt();

		assertThat(prompt).contains("MULTI-AGENT").contains("\"agents\"").contains("outputTypeName");
	}

	private static SingleShotProcessSpecGenerator generator(String answer) {
		return new SingleShotProcessSpecGenerator((system, user) -> answer, "the multi-agent prompt");
	}

}
