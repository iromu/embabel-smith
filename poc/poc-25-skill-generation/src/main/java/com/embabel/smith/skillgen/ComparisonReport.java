/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.embabel.smith.skillgen;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders one blueprint's result (the generated skill, the evals used, and the with/
 * without-skill benchmark per arm) into report lines for {@code results/comparison.md}.
 *
 * @author Ivan Rodriguez
 */
public final class ComparisonReport {

	private ComparisonReport() {
	}

	/**
	 * Renders the blueprint's section.
	 * @param blueprint the blueprint name.
	 * @param skill the generated skill.
	 * @param evals the suite that was run.
	 * @param evalSource the evals source label (bundled or generated).
	 * @param arms the arms, in report order.
	 * @return the report lines.
	 */
	public static List<String> render(String blueprint, Skill skill, EvalSuite evals, String evalSource, List<Arm> arms) {
		List<String> lines = new ArrayList<>();
		lines.add("## Blueprint: " + blueprint);
		lines.add("");
		lines.add("- **Skill:** `" + skill.name() + "` (" + skillMdLines(skill) + " lines of SKILL.md)");
		lines.add("- **Description:** " + skill.description());
		lines.add("- **Evals:** " + evals.evals().size() + " scenario evals (" + evalSource + ")");
		lines.add("");
		lines.add("| Arm | With-skill pass | No-skill pass | Delta (skill lift) |");
		lines.add("|-----|:---------------:|:-------------:|:------------------:|");
		for (Arm arm : arms) {
			lines.add("| " + arm.name() + " | " + pct(arm.summary().withPassRate()) + " | "
					+ pct(arm.summary().withoutPassRate()) + " | " + delta(arm.summary().delta()) + " |");
		}
		lines.add("");
		for (Arm arm : arms) {
			lines.add("### Arm: " + arm.name());
			lines.add("");
			lines.add("| Eval | Scenario | With-skill | No-skill |");
			lines.add("|------|----------|:----------:|:--------:|");
			for (Benchmark.EvalScore score : arm.summary().perEval()) {
				lines.add("| " + score.evalId() + " | " + truncate(score.prompt(), 60) + " | "
						+ score.withPassed() + "/" + score.withTotal() + " | " + score.withoutPassed() + "/"
						+ score.withoutTotal() + " |");
			}
			lines.add("");
		}
		return lines;
	}

	private static int skillMdLines(Skill skill) {
		return (int) skill.toSkillMd().lines().count();
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
	 * @param name the arm label (e.g. "raw", "embabel").
	 * @param runs the runs produced by the arm.
	 * @param summary the aggregated benchmark.
	 */
	public record Arm(String name, List<EvalRunner.EvalRun> runs, Benchmark.Summary summary) {
	}

}
