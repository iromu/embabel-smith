/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.gencmp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.common.model.GoalStepSpec;

import org.junit.jupiter.api.Test;

/**
 * Offline tests for {@link ProcessValidator}: the per-agent and cross-agent soundness
 * checks that the comparison reports on.
 *
 * @author Ivan Rodriguez
 */
class ProcessValidatorTest {

	private static AgentSpec agent(String name, String goalOutput, ActionStepSpec... steps) {
		return new AgentSpec(new AgentSpecManifest(name, "1.0.0"),
				GoalStepSpec.exportedGoal(name + "_goal", "desc", goalOutput), List.of(steps), List.of());
	}

	private static ActionStepSpec step(String name, List<String> inputs, String output) {
		return ActionStepSpec.action(name, "desc", inputs, output, "prompt");
	}

	@Test
	void validTwoAgentProcessIsConnectedWithSharedType() {
		AgentSpec research = agent("research", "MarketReport", step("analyze", List.of("UserInput"), "MarketReport"));
		AgentSpec decision = agent("decision", "FinalDecision",
				step("decide", List.of("MarketReport"), "FinalDecision"));
		ProcessValidator.ValidationResult result = ProcessValidator
			.validate(new ProcessSpec("trading", List.of(research, decision)));
		assertThat(result.valid()).isTrue();
		assertThat(result.connected()).isTrue();
		assertThat(result.sharedTypes()).isEqualTo(1);
	}

	@Test
	void unreachableGoalIsInvalid() {
		AgentSpec agent = agent("broken", "FinalDecision", step("analyze", List.of("UserInput"), "MarketReport"));
		ProcessValidator.ValidationResult result = ProcessValidator.validate(ProcessSpec.single("broken", agent));
		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch((i) -> i.contains("not produced by any of its own steps"));
		assertThat(result.issues()).anyMatch((i) -> i.contains("not reachable from the process entry"));
	}

	@Test
	void danglingInputIsInvalid() {
		AgentSpec agent = agent("broken", "FinalDecision", step("decide", List.of("Mystery"), "FinalDecision"));
		ProcessValidator.ValidationResult result = ProcessValidator.validate(ProcessSpec.single("broken", agent));
		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch((i) -> i.contains("no agent produces"));
	}

	@Test
	void goalNameCollisionIsInvalid() {
		AgentSpec a1 = agent("a1", "Out1", step("s1", List.of("UserInput"), "Out1"));
		AgentSpec a2 = agent("a2", "Out2", step("s2", List.of("Out1"), "Out2"));
		// Force the same goal name on both agents.
		AgentSpec a2Colliding = new AgentSpec(new AgentSpecManifest("a2", "1.0.0"),
				GoalStepSpec.exportedGoal("a1_goal", "desc", "Out2"), List.of(step("s2", List.of("Out1"), "Out2")),
				List.of());
		ProcessValidator.ValidationResult result = ProcessValidator
			.validate(new ProcessSpec("p", List.of(a1, a2Colliding)));
		assertThat(result.valid()).isFalse();
		assertThat(result.issues()).anyMatch((i) -> i.contains("exported by more than one agent"));
	}

	@Test
	void disconnectedSecondAgentIsInvalid() {
		AgentSpec research = agent("research", "MarketReport", step("analyze", List.of("UserInput"), "MarketReport"));
		AgentSpec orphan = agent("orphan", "Other", step("produce", List.of("Mystery"), "Other"));
		ProcessValidator.ValidationResult result = ProcessValidator
			.validate(new ProcessSpec("p", List.of(research, orphan)));
		assertThat(result.valid()).isFalse();
		assertThat(result.connected()).isFalse();
		assertThat(result.issues()).anyMatch((i) -> i.contains("not connected to the process"));
	}

}
