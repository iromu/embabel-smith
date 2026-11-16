/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.gencmp;

import java.util.ArrayList;
import java.util.List;

import com.embabel.smith.common.model.AgentSpec;

/**
 * Renders one generation result (a {@link ProcessSpec} plus its validation) into report
 * lines for the comparison.
 *
 * @author Ivan Rodriguez
 */
public final class ComparisonReport {

	private ComparisonReport() {
	}

	public static List<String> render(String label, ProcessSpec process, ProcessValidator.ValidationResult result) {
		List<String> lines = new ArrayList<>();
		lines.add("=== " + label + " ===");
		lines.add("process: " + process.process());
		lines.add("agents: " + process.agents().size() + " | steps: " + process.totalSteps() + " | shared types: "
				+ result.sharedTypes() + " | connected: " + result.connected());
		lines.add("verdict: " + (result.valid() ? "VALID" : "INVALID (" + result.issues().size() + " issues)"));
		for (AgentSpec agent : process.agents()) {
			String goal = (agent.goal() != null) ? agent.goal().outputTypeName() : "(no goal)";
			lines.add("  - " + agent.name() + " [goal: " + goal + "] " + agent.steps().size() + " steps, "
					+ agent.types().size() + " types");
			for (var step : agent.steps()) {
				lines.add("      * " + step.name() + ": " + step.inputTypeNames() + " -> " + step.outputTypeName());
			}
		}
		for (String issue : result.issues()) {
			lines.add("  ! " + issue);
		}
		lines.add("");
		return lines;
	}

}
