/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.skillgen;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders one skill's result (the generated skill, the evals used, and the with/
 * without-skill benchmark per arm) into report lines for {@code results/comparison.md}. A
 * blueprint decomposes into several skills, each rendered as its own section under the
 * blueprint's header.
 *
 * @author Ivan Rodriguez
 */
public final class ComparisonReport {

	private ComparisonReport() {
	}

	/**
	 * Renders one skill's section.
	 * @param skill the generated skill.
	 * @param evals the per-skill suite that was run.
	 * @param arms the arms, in report order.
	 * @return the report lines.
	 */
	public static List<String> renderSkill(Skill skill, EvalSuite evals, List<Arm> arms) {
		List<String> lines = new ArrayList<>();
		lines.add("### Skill: " + skill.name());
		lines.add("");
		lines.add("- **Description:** " + skill.description());
		lines.add("- **Evals:** " + evals.evals().size() + " scenario evals (generated per-skill)");
		lines.add("");
		lines.add("| Arm | With-skill pass | No-skill pass | Delta (skill lift) |");
		lines.add("|-----|:---------------:|:-------------:|:------------------:|");
		for (Arm arm : arms) {
			lines.add("| " + arm.name() + " | " + pct(arm.summary().withPassRate()) + " | "
					+ pct(arm.summary().withoutPassRate()) + " | " + delta(arm.summary().delta()) + " |");
		}
		lines.add("");
		for (Arm arm : arms) {
			lines.add("#### Arm: " + arm.name());
			lines.add("");
			lines.add("| Eval | Scenario | With-skill | No-skill |");
			lines.add("|------|----------|:----------:|:--------:|");
			for (Benchmark.EvalScore score : arm.summary().perEval()) {
				lines.add("| " + score.evalId() + " | " + truncate(score.prompt(), 60) + " | " + score.withPassed()
						+ "/" + score.withTotal() + " | " + score.withoutPassed() + "/" + score.withoutTotal() + " |");
			}
			lines.add("");
		}
		return lines;
	}

	private static String pct(double rate) {
		return String.format("%5.0f%%", rate * 100);
	}

	private static String delta(double d) {
		return String.format("%+5.0f%%", d * 100);
	}

	private static String truncate(String s, int max) {
		String one = (s != null) ? s.replaceAll("\\s+", " ") : "";
		return (one.length() <= max) ? one : one.substring(0, max - 1) + "…";
	}

	/**
	 * An arm's result: its runs and aggregated summary.
	 *
	 * @param name the arm label (e.g. "raw", "embabel").
	 * @param runs the runs produced by the arm.
	 * @param summary the aggregated benchmark.
	 */
	public record Arm(String name, List<EvalRunner.EvalRun> runs, Benchmark.Summary summary) {
	}

}
