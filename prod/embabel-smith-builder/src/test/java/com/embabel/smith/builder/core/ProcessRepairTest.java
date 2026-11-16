/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.common.model.DynamicTypeDef;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.model.ProcessSpec;
import com.embabel.smith.common.model.PropertyDef;
import com.embabel.smith.common.validation.ProcessValidationResult;
import com.embabel.smith.common.validation.ProcessValidator;
import org.junit.jupiter.api.Test;

/**
 * Offline tests for the bounded cycle repair: the cycle a reorder settles, the one no
 * order can settle, and the process that never needed one.
 *
 * @author Ivan Rodriguez
 */
class ProcessRepairTest {

	private final ProcessValidator validator = new ProcessValidator();

	@Test
	void aCycleAReorderCanBreakIsRepairedAndRevalidates() {
		// The model narrated the decision agent first, yet its input is the research
		// agent's output — the declared order asks research to run after what it feeds.
		ProcessSpec declared = process(
				agent("decision", "decision_goal", "FinalDecision",
						step("decide", List.of("MarketReport"), "FinalDecision")),
				agent("research", "research_goal", "MarketReport",
						step("analyze", List.of("UserInput"), "MarketReport")));
		ProcessValidationResult found = this.validator.validate(declared);
		assertThat(found.hasCycle()).isTrue();

		ProcessRepair.Result repaired = ProcessRepair.repair(declared, found);

		assertThat(repaired.repaired()).isTrue();
		assertThat(repaired.attempts()).isLessThanOrEqualTo(ProcessRepair.MAX_ATTEMPTS);
		assertThat(repaired.process().agents()).extracting(AgentSpec::name).containsExactly("research", "decision");
		assertThat(repaired.validation().isValid()).as(() -> repaired.validation().issues().toString()).isTrue();
	}

	@Test
	void aCycleNoOrderCanBreakLeavesTheProcessBlocked() {
		ProcessSpec mutual = process(
				agent("market", "market_goal", "MarketReport", step("analyze", List.of("UserInput"), "MarketReport"),
						step("refine", List.of("RiskReport"), "MarketReport")),
				agent("risk", "risk_goal", "RiskReport", step("assess", List.of("UserInput"), "RiskReport"),
						step("check", List.of("MarketReport"), "RiskReport")));
		ProcessValidationResult found = this.validator.validate(mutual);
		assertThat(found.hasCycle()).isTrue();

		ProcessRepair.Result repaired = ProcessRepair.repair(mutual, found);

		assertThat(repaired.repaired()).isFalse();
		assertThat(repaired.attempts()).isZero();
		assertThat(repaired.validation().hasCycle()).isTrue();
		assertThat(repaired.validation().isValid()).isFalse();
	}

	@Test
	void aProcessWithoutACycleIsLeftAlone() {
		ProcessSpec sound = process(
				agent("research", "research_goal", "MarketReport",
						step("analyze", List.of("UserInput"), "MarketReport")),
				agent("decision", "decision_goal", "FinalDecision",
						step("decide", List.of("MarketReport"), "FinalDecision")));

		ProcessRepair.Result repaired = ProcessRepair.repair(sound, this.validator.validate(sound));

		assertThat(repaired.attempts()).isZero();
		assertThat(repaired.repaired()).isFalse();
		assertThat(repaired.process()).isEqualTo(sound);
		assertThat(repaired.validation().isValid()).isTrue();
	}

	private static ProcessSpec process(AgentSpec... agents) {
		return new ProcessSpec("trading", List.of(agents));
	}

	private static AgentSpec agent(String name, String goalName, String goalOutput, ActionStepSpec... steps) {
		return new AgentSpec(new AgentSpecManifest(name, "1.0.0"),
				GoalStepSpec.exportedGoal(goalName, "desc", goalOutput), List.of(steps),
				List.of(new com.embabel.smith.common.model.DynamicTypeDef(goalOutput, "the " + goalOutput + " data",
						List.of(new com.embabel.smith.common.model.PropertyDef("content", "string", "the payload")),
						List.of(), true)));
	}

	private static ActionStepSpec step(String name, List<String> inputs, String output) {
		return ActionStepSpec.action(name, "desc", inputs, output, "prompt");
	}

}
