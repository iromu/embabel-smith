/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.gencmp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Offline tests for {@link SingleShotGenerator}: the JSON extraction, flat and multi
 * parsing, and normalisation, using a stub {@link LlmCaller}.
 *
 * @author Ivan Rodriguez
 */
class SingleShotGeneratorTest {

	private static final String FLAT_JSON = """
			{
			  "manifest": { "name": "trading_process", "version": "1.0.0" },
			  "goal": { "stepType": "goal", "name": "final_decision", "description": "the final decision",
			            "outputTypeName": "FinalDecision", "export": true },
			  "steps": [
			    { "stepType": "action", "name": "analyze_market", "description": "analyze",
			      "inputTypeNames": ["UserInput"], "outputTypeName": "MarketReport", "prompt": "{{ userInput }}" },
			    { "stepType": "action", "name": "decide", "description": "decide",
			      "inputTypeNames": ["MarketReport"], "outputTypeName": "FinalDecision", "prompt": "{{ marketReport }}" }
			  ],
			  "types": [ { "name": "MarketReport", "description": "market report" } ]
			}
			""";

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
			          "inputTypeNames": ["UserInput"], "outputTypeName": "MarketReport", "prompt": "{{ userInput }}" }
			      ],
			      "types": [ { "name": "MarketReport", "description": "mr" } ]
			    },
			    {
			      "manifest": { "name": "decision", "version": "1.0.0" },
			      "goal": { "stepType": "goal", "name": "decision_goal", "description": "decision done",
			                "outputTypeName": "FinalDecision", "export": true },
			      "steps": [
			        { "stepType": "action", "name": "decide", "description": "d",
			          "inputTypeNames": ["MarketReport"], "outputTypeName": "FinalDecision", "prompt": "{{ marketReport }}" }
			      ],
			      "types": [ { "name": "FinalDecision", "description": "fd" } ]
			    }
			  ]
			}
			""";

	private final SingleShotGenerator generator = new SingleShotGenerator((system, user) -> FLAT_JSON, "FLAT", "MULTI");

	private final SingleShotGenerator multiGenerator = new SingleShotGenerator((system, user) -> MULTI_JSON, "FLAT",
			"MULTI");

	@Test
	void parsesFlatIntoSingleAgent() {
		ProcessSpec process = this.generator.generateFlat("a business process");
		assertThat(process.agents()).hasSize(1);
		assertThat(process.process()).isEqualTo("trading_process");
		assertThat(process.agents().get(0).goal().outputTypeName()).isEqualTo("FinalDecision");
		assertThat(process.agents().get(0).goal().isExported()).isTrue();
		assertThat(process.totalSteps()).isEqualTo(2);
	}

	@Test
	void parsesMultiIntoOrderedAgents() {
		ProcessSpec process = this.multiGenerator.generateMulti("a business process");
		assertThat(process.process()).isEqualTo("trading");
		assertThat(process.agents()).hasSize(2);
		assertThat(process.agents().get(0).name()).isEqualTo("research");
		assertThat(process.agents().get(1).name()).isEqualTo("decision");
		assertThat(process.totalSteps()).isEqualTo(2);
		assertThat(process.finalOutputType()).isEqualTo("FinalDecision");
	}

	@Test
	void normalisesMissingManifestAndExport() {
		String json = """
				{
				  "goal": { "stepType": "goal", "name": "g", "description": "d", "outputTypeName": "Out", "export": false },
				  "steps": [
				    { "stepType": "action", "name": "s", "description": "s",
				      "inputTypeNames": ["UserInput"], "outputTypeName": "Out", "prompt": "p" }
				  ]
				}
				""";
		SingleShotGenerator gen = new SingleShotGenerator((system, user) -> json, "FLAT", "MULTI");
		ProcessSpec process = gen.generateFlat("x");
		assertThat(process.agents().get(0).manifest().name()).isEqualTo("g");
		assertThat(process.agents().get(0).goal().isExported()).isTrue();
	}

	@Test
	void extractJsonStripsMarkdownFencesAndProse() {
		String raw = "Here is the spec:\n```json\n{ \"a\": 1 }\n```\nHope that helps.";
		assertThat(SingleShotGenerator.extractJson(raw)).isEqualTo("{ \"a\": 1 }");
	}

	@Test
	void rejectsAnswerWithoutJson() {
		SingleShotGenerator gen = new SingleShotGenerator((system, user) -> "no json here", "FLAT", "MULTI");
		assertThatThrownBy(() -> gen.generateFlat("x")).isInstanceOf(IllegalStateException.class);
	}

}
