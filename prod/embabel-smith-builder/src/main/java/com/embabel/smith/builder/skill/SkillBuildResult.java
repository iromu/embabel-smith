/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.skill;

import java.util.List;

import com.embabel.smith.builder.BuildResult;
import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.builder.publish.PublishResult;
import com.embabel.smith.builder.security.ScanReport;

/**
 * The outcome of a skill build: whether the skill was published or the build was blocked,
 * at which stage it stopped, and the evidence. Where {@link BuildResult} carries a single
 * deterministic eval score, this carries the two arms the skill gate compares — the
 * with-skill and no-skill pass rates and the lift between them — plus the per-scenario
 * grading, so a build blocked on the lift is inspectable rather than merely refused.
 *
 * @param status the build status (built or blocked).
 * @param stage the stage at which the build stopped.
 * @param skillName the name of the built skill.
 * @param withPassRate the with-skill pass rate.
 * @param withoutPassRate the no-skill baseline pass rate.
 * @param lift the with-skill pass rate minus the baseline.
 * @param issues the issues that blocked the build.
 * @param perEval the per-scenario pass counts for both arms.
 * @param scan the security scan report for the skill.
 * @param lifecycle the lifecycle metadata stamped on the skill.
 * @param published the publish result when the build succeeded.
 * @author Ivan Rodriguez
 */
public record SkillBuildResult(BuildResult.Status status, String stage, String skillName, Double withPassRate,
		Double withoutPassRate, Double lift, List<String> issues, List<Benchmark.EvalScore> perEval, ScanReport scan,
		LifecycleMetadata lifecycle, PublishResult published) {

	public SkillBuildResult {
		issues = (issues != null) ? List.copyOf(issues) : List.of();
		perEval = (perEval != null) ? List.copyOf(perEval) : List.of();
	}

	public boolean isBuilt() {
		return this.status == BuildResult.Status.BUILT;
	}

	public static SkillBuildResult built(String skillName, Benchmark.Summary summary, ScanReport scan,
			LifecycleMetadata lifecycle, PublishResult published) {
		return new SkillBuildResult(BuildResult.Status.BUILT, "done", skillName, summary.withPassRate(),
				summary.withoutPassRate(), summary.lift(), List.of(), summary.perEval(), scan, lifecycle, published);
	}

	public static SkillBuildResult blocked(String stage, String skillName, List<String> issues,
			Benchmark.Summary summary, ScanReport scan) {
		return new SkillBuildResult(BuildResult.Status.BLOCKED, stage, skillName,
				(summary != null) ? summary.withPassRate() : null, (summary != null) ? summary.withoutPassRate() : null,
				(summary != null) ? summary.lift() : null, issues, (summary != null) ? summary.perEval() : List.of(),
				scan, null, null);
	}

	/** A human-readable build report for stdout. */
	public String report() {
		StringBuilder sb = new StringBuilder();
		if (isBuilt()) {
			sb.append("SKILL BUILD OK: ").append(this.skillName).append('\n');
			appendLift(sb);
			if (this.scan != null) {
				sb.append("  security:    ").append(this.scan.summary()).append('\n');
			}
			if (this.lifecycle != null) {
				sb.append("  lifecycle:   ")
					.append(this.lifecycle.state())
					.append(" (hash ")
					.append(shortHash(this.lifecycle.contentHash()))
					.append(", baseline ")
					.append(String.format("%.3f", this.lifecycle.baselinePassRate()))
					.append(")\n");
			}
			if (this.published != null) {
				sb.append("  published:   ").append(this.published.location());
				if (this.published.pushed()) {
					sb.append(" (pushed)");
				}
				sb.append('\n');
			}
		}
		else {
			sb.append("SKILL BUILD BLOCKED at ").append(this.stage).append(" (").append(this.skillName).append(")\n");
			if (this.lift != null) {
				appendLift(sb);
			}
			for (String issue : this.issues) {
				sb.append("  - ").append(issue).append('\n');
			}
			for (Benchmark.EvalScore score : this.perEval) {
				sb.append("  eval ")
					.append(score.evalId())
					.append(" \"")
					.append(abbrev(score.prompt()))
					.append("\": with ")
					.append(score.withPassed())
					.append('/')
					.append(score.withTotal())
					.append(" vs without ")
					.append(score.withoutPassed())
					.append('/')
					.append(score.withoutTotal())
					.append('\n');
			}
		}
		return sb.toString();
	}

	private void appendLift(StringBuilder sb) {
		sb.append("  skill lift:  ")
			.append(String.format("%+.3f", this.lift))
			.append(" (with ")
			.append(String.format("%.3f", this.withPassRate))
			.append(" vs baseline ")
			.append(String.format("%.3f", this.withoutPassRate))
			.append(")\n");
	}

	private static String abbrev(String prompt) {
		if (prompt == null) {
			return "";
		}
		return (prompt.length() <= 60) ? prompt : prompt.substring(0, 60) + "...";
	}

	private static String shortHash(String hash) {
		return (hash == null || hash.length() <= 12) ? String.valueOf(hash) : hash.substring(0, 12);
	}
}
