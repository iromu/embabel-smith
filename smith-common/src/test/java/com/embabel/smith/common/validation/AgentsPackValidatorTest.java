/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.model.AgentsPack;
import org.junit.jupiter.api.Test;

/**
 * Offline tests for the cross-agent gate: what makes an agents-pack sound, and the two
 * shapes of circular dependency — the one a reorder fixes and the one it cannot.
 *
 * @author Ivan Rodriguez
 */
class AgentsPackValidatorTest {

	private final AgentsPackValidator validator = new AgentsPackValidator();

	@Test
	void wellFormedPackIsValidAndSharesAType() {
		AgentsPack agentsPack = agentsPack("trading",
				agent("research", "research_goal", "MarketReport",
						step("analyze", List.of("UserInput"), "MarketReport")),
				agent("decision", "decision_goal", "FinalDecision",
						step("decide", List.of("MarketReport"), "FinalDecision")));

		AgentsPackValidationResult result = this.validator.validate(agentsPack);

		assertThat(result.isValid()).as(() -> result.issues().toString()).isTrue();
		assertThat(result.connected()).isTrue();
		assertThat(result.sharedTypes()).isEqualTo(1);
		assertThat(result.hasCycle()).isFalse();
	}

	@Test
	void declaredOrderThatContradictsTheDataFlowIsAReportedCycle() {
		// The model listed the decision agent first, but its input is the research
		// agent's output: the run order asks research to run after what it feeds.
		AgentsPack agentsPack = agentsPack("trading",
				agent("decision", "decision_goal", "FinalDecision",
						step("decide", List.of("MarketReport"), "FinalDecision")),
				agent("research", "research_goal", "MarketReport",
						step("analyze", List.of("UserInput"), "MarketReport")));

		AgentsPackValidationResult result = this.validator.validate(agentsPack);

		assertThat(result.hasCycle()).isTrue();
		assertThat(result.cycle()).containsExactly("decision", "research");
		assertThat(result.issues()).anyMatch((i) -> i.contains("Circular dependency"));
	}

	@Test
	void agentsThatConsumeEachOthersOutputsAreAReportedCycle() {
		AgentsPack agentsPack = agentsPack("pingpong",
				agent("market", "market_goal", "MarketReport", step("analyze", List.of("UserInput"), "MarketReport"),
						step("refine", List.of("RiskReport"), "MarketReport")),
				agent("risk", "risk_goal", "RiskReport", step("assess", List.of("UserInput"), "RiskReport"),
						step("check", List.of("MarketReport"), "RiskReport")));

		AgentsPackValidationResult result = this.validator.validate(agentsPack);

		assertThat(result.hasCycle()).isTrue();
		assertThat(result.isValid()).isFalse();
	}

	@Test
	void anAgentNothingFeedsIsFlagged() {
		AgentsPack agentsPack = agentsPack("trading",
				agent("research", "research_goal", "MarketReport",
						step("analyze", List.of("UserInput"), "MarketReport")),
				agent("orphan", "orphan_goal", "Other", step("produce", List.of("Mystery"), "Other")));

		AgentsPackValidationResult result = this.validator.validate(agentsPack);

		assertThat(result.isValid()).isFalse();
		assertThat(result.connected()).isFalse();
		assertThat(result.issues()).anyMatch((i) -> i.contains("'orphan' is not connected to the agents-pack"));
	}

	@Test
	void aStepInputNoAgentProducesIsFlagged() {
		AgentsPack agentsPack = agentsPack("broken",
				agent("solo", "solo_goal", "FinalDecision", step("decide", List.of("Mystery"), "FinalDecision")));

		AgentsPackValidationResult result = this.validator.validate(agentsPack);

		assertThat(result.isValid()).isFalse();
		assertThat(result.issues()).anyMatch((i) -> i.contains("needs input 'Mystery', which no agent produces"));
	}

	@Test
	void aGoalItsAgentNeverProducesIsFlagged() {
		AgentsPack agentsPack = agentsPack("broken",
				agent("solo", "solo_goal", "FinalDecision", step("analyze", List.of("UserInput"), "MarketReport")));

		AgentsPackValidationResult result = this.validator.validate(agentsPack);

		assertThat(result.isValid()).isFalse();
		assertThat(result.issues()).anyMatch((i) -> i.contains("is not produced by any of its own steps"));
	}

	@Test
	void twoAgentsExportingTheSameGoalNameAreFlagged() {
		AgentsPack agentsPack = agentsPack("dupe",
				agent("a1", "same_goal", "Out1", step("s1", List.of("UserInput"), "Out1")),
				agent("a2", "same_goal", "Out2", step("s2", List.of("Out1"), "Out2")));

		AgentsPackValidationResult result = this.validator.validate(agentsPack);

		assertThat(result.isValid()).isFalse();
		assertThat(result.issues()).anyMatch((i) -> i.contains("exported by more than one agent"));
	}

	@Test
	void aFullyQualifiedUserInputStillCountsAsTheEntry() {
		AgentsPack agentsPack = agentsPack("fqn", agent("solo", "solo_goal", "FinalDecision",
				step("decide", List.of("com.embabel.agent.domain.io.UserInput"), "FinalDecision")));

		AgentsPackValidationResult result = this.validator.validate(agentsPack);

		assertThat(result.isValid()).as(() -> result.issues().toString()).isTrue();
	}

	@Test
	void aPackWithNoAgentsIsInvalid() {
		AgentsPackValidationResult result = this.validator.validate(new AgentsPack("empty", List.of()));

		assertThat(result.isValid()).isFalse();
		assertThat(result.issues()).containsExactly("The agents-pack has no agents.");
	}

	private static AgentsPack agentsPack(String name, AgentSpec... agents) {
		return new AgentsPack(name, List.of(agents));
	}

	private static AgentSpec agent(String name, String goalName, String goalOutput, ActionStepSpec... steps) {
		return new AgentSpec(new AgentSpecManifest(name, "1.0.0"),
				GoalStepSpec.exportedGoal(goalName, "desc", goalOutput), List.of(steps), List.of());
	}

	private static ActionStepSpec step(String name, List<String> inputs, String output) {
		return ActionStepSpec.action(name, "desc", inputs, output, "prompt");
	}

}
