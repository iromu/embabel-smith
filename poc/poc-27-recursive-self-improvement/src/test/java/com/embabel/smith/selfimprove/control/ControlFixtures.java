/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.control;

import java.util.List;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.selfimprove.referee.Candidate;
import com.embabel.smith.selfimprove.referee.RefereeReport;

/**
 * Fixtures for the human-control tests: a candidate and the referee verdicts the hold
 * path reads. The pack is the same shape the referee's own fixtures build, restated here
 * rather than imported because {@code RefereeFixtures} is package-private to
 * {@code selfimprove.referee} and this package may not edit it.
 *
 * <p>
 * The reports are written by hand rather than produced by a referee on purpose: the hold
 * path is about what a verdict does to the queue, and building the verdict keeps the test
 * offline with no endpoint and no invoker. What the reports must be honest about is the
 * shape a gated {@code RefereeReport} has — gated, scored, with the frozen task ids it
 * was scored on — and the refused case in {@code HeldForApprovalTest} is there because a
 * hand-built report is exactly where a wrong shape shows up.
 *
 * @author Ivan Rodriguez
 */
final class ControlFixtures {

	private ControlFixtures() {
	}

	/**
	 * A candidate with no proposed tasks.
	 * @param name the variant name
	 * @return the candidate
	 */
	static Candidate candidate(String name) {
		return new Candidate(name, pack(name), null);
	}

	/**
	 * A report recording that the candidate cleared every gate.
	 * @param name the variant name
	 * @param gateScore the gate's re-eval pass-rate
	 * @param frozenScore the pass-rate under the frozen set's own check
	 * @return the report
	 */
	static RefereeReport gated(String name, double gateScore, double frozenScore) {
		return new RefereeReport(name, true, null, List.of(), gateScore, frozenScore, List.of(),
				List.of("b27-t01", "b27-t02", "b27-t03", "b27-t04", "b27-t05", "b27-t06"), List.of());
	}

	/**
	 * A pack to hold, in the shape the gate accepts.
	 * @param name the pack name
	 * @return a structurally valid pack
	 */
	static AgentSpec pack(String name) {
		ActionStepSpec step = ActionStepSpec.action("answer", "step answer", List.of("UserInput"), "BackgroundMessage",
				"answer the question carefully");
		return new AgentSpec(new AgentSpecManifest(name, "1.0.0"),
				GoalStepSpec.exportedGoal(name, "goal " + name, "BackgroundMessage"), List.of(step), List.of());
	}

}
