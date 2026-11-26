/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder;

import java.util.List;

import com.embabel.smith.builder.cli.BuildFlow;
import com.embabel.smith.builder.core.Recommendation;
import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.builder.publish.PublishResult;
import com.embabel.smith.builder.security.ScanReport;
import com.embabel.smith.common.model.AgentSpec;

/**
 * The outcome of a build: whether the agent spec was published or the build was blocked,
 * at which stage it stopped, and the evidence (eval score, security scan, lifecycle
 * metadata, publish location). A blocked build names the stage and the reasons it
 * stopped; when the build could not be resolved cleanly (an ambiguous/invalid
 * description) it carries the draft for review.
 *
 * @param status the build status (built or blocked).
 * @param stage the stage at which the build stopped.
 * @param packName the name of the built agent spec pack.
 * @param score the eval score of the built pack.
 * @param issues the issues that blocked the build.
 * @param scan the security scan report for the pack.
 * @param lifecycle the lifecycle metadata stamped on the pack.
 * @param published the publish result when the build succeeded.
 * @param draft the retained draft when the build could not be resolved.
 * @param recommendation the build's choice between its two candidates, and the scores it
 * was made on; {@code null} for a build that never got that far.
 * @param flow the flow that ran the build, as {@code smith build --flow} names it;
 * {@code null} when nothing stamped one. The stamp is the CLI's, because there is
 * deliberately no flow field in {@code BuildOptions} to carry it through the build
 * (design D3 of the flow flag): a driver that calls {@code BuilderService} directly — the
 * studio, the improvement loop — gets a result with no flow named, and the report says
 * nothing rather than guess.
 * @author Ivan Rodriguez
 */
public record BuildResult(Status status, String stage, String packName, Double score, List<String> issues,
		ScanReport scan, LifecycleMetadata lifecycle, PublishResult published, AgentSpec draft,
		Recommendation recommendation, BuildFlow flow) {

	public enum Status {

		/** The build succeeded and the pack was published. */
		BUILT,

		/** The build stopped at a gate before publishing. */
		BLOCKED

	}

	public boolean isBuilt() {
		return this.status == Status.BUILT;
	}

	public static BuildResult built(String packName, double score, ScanReport scan, LifecycleMetadata lifecycle,
			PublishResult published) {
		return built(packName, score, scan, lifecycle, published, null);
	}

	public static BuildResult built(String packName, double score, ScanReport scan, LifecycleMetadata lifecycle,
			PublishResult published, Recommendation recommendation) {
		return new BuildResult(Status.BUILT, "done", packName, score, List.of(), scan, lifecycle, published, null,
				recommendation, null);
	}

	public static BuildResult blocked(String stage, String packName, List<String> issues, Double score, ScanReport scan,
			AgentSpec draft) {
		return new BuildResult(Status.BLOCKED, stage, packName, score,
				(issues != null) ? List.copyOf(issues) : List.of(), scan, null, null, draft, null, null);
	}

	/**
	 * The same outcome with the flow that produced it named, which is the last thing the
	 * driver gets to say about a build. Copy rather than constructor argument: the flow
	 * is the CLI's switch and {@code BuilderService} — which does not know it, by design
	 * — is what constructs the result.
	 * @param flow the flow that ran this build
	 * @return the result, carrying the flow
	 */
	public BuildResult withFlow(BuildFlow flow) {
		return new BuildResult(this.status, this.stage, this.packName, this.score, this.issues, this.scan,
				this.lifecycle, this.published, this.draft, this.recommendation, flow);
	}

	/** A human-readable build report for stdout. */
	public String report() {
		StringBuilder sb = new StringBuilder();
		if (isBuilt()) {
			sb.append("BUILD OK: ").append(this.packName).append('\n');
			appendFlow(sb);
			if (this.recommendation != null) {
				sb.append("  candidate:   ").append(this.recommendation.reason()).append('\n');
				Double agentsPackScore = (null != this.recommendation.agentsPack())
						? this.recommendation.agentsPack().score() : null;
				sb.append("  candidates:  single ")
					.append(formatted(this.recommendation.single().score()))
					.append(", agents-pack ")
					.append(formatted(agentsPackScore))
					.append('\n');
			}
			sb.append("  eval score:  ").append(String.format("%.3f", this.score)).append('\n');
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
			sb.append("BUILD BLOCKED at ").append(this.stage).append(" (").append(this.packName).append(")\n");
			appendFlow(sb);
			if (this.score != null) {
				sb.append("  eval score:  ").append(String.format("%.3f", this.score)).append('\n');
			}
			for (String issue : this.issues) {
				sb.append("  - ").append(issue).append('\n');
			}
			if (this.draft != null) {
				sb.append("  draft:       ").append(this.draft.name()).append(" (retained for review)").append('\n');
			}
		}
		return sb.toString();
	}

	/**
	 * The line that makes a run attributable from its output rather than from how it was
	 * launched: the flow that produced it, as {@code --flow} names it. Absent when no
	 * driver stamped one, which is the honest reading — the report does not invent a flow
	 * for a build it cannot name.
	 */
	private void appendFlow(StringBuilder sb) {
		if (this.flow != null) {
			sb.append("  flow:        ").append(this.flow.id()).append('\n');
		}
	}

	private static String shortHash(String hash) {
		return (hash == null || hash.length() <= 12) ? String.valueOf(hash) : hash.substring(0, 12);
	}

	private static String formatted(Double score) {
		return (null != score) ? String.format("%.3f", score) : "not scored";
	}
}
