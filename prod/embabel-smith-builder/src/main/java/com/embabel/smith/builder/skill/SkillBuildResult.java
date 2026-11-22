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
 * @param inspection what the inspection gate ran and found, or {@code null} for a build
 * that stopped before the gate ran.
 * @author Ivan Rodriguez
 */
public record SkillBuildResult(BuildResult.Status status, String stage, String skillName, Double withPassRate,
		Double withoutPassRate, Double lift, List<String> issues, List<Benchmark.EvalScore> perEval, ScanReport scan,
		LifecycleMetadata lifecycle, PublishResult published, InspectionReport inspection) {

	public SkillBuildResult {
		issues = (issues != null) ? List.copyOf(issues) : List.of();
		perEval = (perEval != null) ? List.copyOf(perEval) : List.of();
	}

	public boolean isBuilt() {
		return this.status == BuildResult.Status.BUILT;
	}

	public static SkillBuildResult built(String skillName, Benchmark.Summary summary, ScanReport scan,
			LifecycleMetadata lifecycle, PublishResult published, InspectionReport inspection) {
		return new SkillBuildResult(BuildResult.Status.BUILT, "done", skillName, summary.withPassRate(),
				summary.withoutPassRate(), summary.lift(), List.of(), summary.perEval(), scan, lifecycle, published,
				inspection);
	}

	public static SkillBuildResult blocked(String stage, String skillName, List<String> issues,
			Benchmark.Summary summary, ScanReport scan) {
		return blocked(stage, skillName, issues, summary, scan, null);
	}

	public static SkillBuildResult blocked(String stage, String skillName, List<String> issues,
			Benchmark.Summary summary, ScanReport scan, InspectionReport inspection) {
		return new SkillBuildResult(BuildResult.Status.BLOCKED, stage, skillName,
				(summary != null) ? summary.withPassRate() : null, (summary != null) ? summary.withoutPassRate() : null,
				(summary != null) ? summary.lift() : null, issues, (summary != null) ? summary.perEval() : List.of(),
				scan, null, null, inspection);
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
			appendInspection(sb);
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
			appendInspection(sb);
		}
		return sb.toString();
	}

	/**
	 * The inspection gate's own lines: what it ran, the score it gave, how many drafts it
	 * took, what it advised, and — always, whether or not anything came back incomplete —
	 * what it did not scan. A default build has to state plainly that it did no secret or
	 * code-risk scanning, because the absence of a complaint is not evidence that a scan
	 * ran.
	 * @param sb the report being built
	 */
	private void appendInspection(StringBuilder sb) {
		if (this.inspection == null) {
			return;
		}
		Double quality = this.inspection.qualityScore();
		int drafts = this.inspection.drafts();
		sb.append("  inspect:     ")
			.append(this.inspection.checksRun())
			.append(" checks run, quality ")
			.append((quality != null) ? String.format("%.1f/100", quality) : "not reported")
			.append(", ")
			.append(drafts)
			.append((drafts == 1) ? " draft" : " drafts")
			.append('\n');
		for (String scanner : this.inspection.incompleteScans()) {
			sb.append("  incomplete:  ").append(scanner).append(" did not complete its scan\n");
		}
		List<String> notRun = this.inspection.checksNotRun();
		sb.append("  not scanned: ").append(notRun.isEmpty() ? "none" : String.join(", ", notRun));
		if (!notRun.isEmpty()) {
			sb.append(" (no secret, security or code-risk scanning; pass --full-inspection to request them)");
		}
		sb.append('\n');
		List<String> advisories = this.inspection.advisoryFindings();
		if (!advisories.isEmpty()) {
			sb.append("  advisory:    ")
				.append(advisories.size())
				.append(" finding")
				.append((advisories.size() == 1) ? "" : "s")
				.append(", none blocking\n");
			for (String advisory : advisories) {
				sb.append("    - ").append(advisory).append('\n');
			}
		}
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

	/**
	 * What one build's inspection gate did, carried on both the built and the blocked
	 * report so a reader can tell a clean skill from an unscanned one.
	 *
	 * @param checksRun how many checks the lineup requested
	 * @param qualityScore the quality check's composite 0-100 score, or {@code null} when
	 * it reported none
	 * @param drafts the number of drafts the gate consumed, including the one it accepted
	 * @param advisoryFindings findings that informed the report and never refused the
	 * build, each labelled with the check that produced it
	 * @param incompleteScans scanners that ran without producing complete evidence
	 * @param checksNotRun checks the lineup did not request, so nothing was scanned for
	 * them
	 * @author Ivan Rodriguez
	 */
	public record InspectionReport(int checksRun, Double qualityScore, int drafts, List<String> advisoryFindings,
			List<String> incompleteScans, List<String> checksNotRun) {

		public InspectionReport {
			advisoryFindings = (advisoryFindings != null) ? List.copyOf(advisoryFindings) : List.of();
			incompleteScans = (incompleteScans != null) ? List.copyOf(incompleteScans) : List.of();
			checksNotRun = (checksNotRun != null) ? List.copyOf(checksNotRun) : List.of();
		}

	}

}
