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

package com.embabel.smith.builder;

import java.util.List;

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
 * @author Ivan Rodriguez
 */
public record BuildResult(Status status, String stage, String packName, Double score, List<String> issues,
		ScanReport scan, LifecycleMetadata lifecycle, PublishResult published, AgentSpec draft) {

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
		return new BuildResult(Status.BUILT, "done", packName, score, List.of(), scan, lifecycle, published, null);
	}

	public static BuildResult blocked(String stage, String packName, List<String> issues, Double score, ScanReport scan,
			AgentSpec draft) {
		return new BuildResult(Status.BLOCKED, stage, packName, score,
				(issues != null) ? List.copyOf(issues) : List.of(), scan, null, null, draft);
	}

	/** A human-readable build report for stdout. */
	public String report() {
		StringBuilder sb = new StringBuilder();
		if (isBuilt()) {
			sb.append("BUILD OK: ").append(this.packName).append('\n');
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

	private static String shortHash(String hash) {
		return (hash == null || hash.length() <= 12) ? String.valueOf(hash) : hash.substring(0, 12);
	}
}
