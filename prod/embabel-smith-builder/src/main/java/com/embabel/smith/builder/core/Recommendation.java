/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.core;

/**
 * What a build decided between its two candidates: which one it publishes, and both
 * scores the decision was made on. The build stores both candidates and publishes this
 * one, so the report carries the runner-up rather than dropping it — the rejected
 * candidate is the evidence that the choice was made rather than assumed.
 *
 * @param chosen the candidate the build publishes
 * @param single the single-agent candidate as scored
 * @param agentsPack the agents-pack candidate as scored, or {@code null} when none was
 * generated
 * @param agentsPackNote what stopped the agents-pack candidate from being published — the
 * generation, repair or validation failure the report should name rather than hide
 * @author Ivan Rodriguez
 */
public record Recommendation(Candidate chosen, Candidate single, Candidate agentsPack, String agentsPackNote) {

	/**
	 * Applies the recommendation rule to the two candidates.
	 * @param single the single-agent candidate
	 * @param agentsPack the agents-pack candidate, or {@code null} when none was
	 * generated
	 * @param agentsPackNote why there is no scored agents-pack candidate, when there is
	 * none
	 * @return the recommendation
	 */
	public static Recommendation choose(Candidate single, Candidate agentsPack, String agentsPackNote) {
		return new Recommendation(CandidateRecommender.recommend(single, agentsPack), single, agentsPack,
				agentsPackNote);
	}

	/** The shape of the candidate the build publishes. */
	public CandidateShape shape() {
		return this.chosen.shape();
	}

	/** The score of the candidate the build publishes. */
	public Double score() {
		return this.chosen.score();
	}

	/** Why this candidate and not the other, for the build report. */
	public String reason() {
		String reason = CandidateRecommender.reason(this.single, this.agentsPack, this.chosen);
		if (this.agentsPackNote != null && !this.agentsPackNote.isBlank()) {
			return reason + " (" + this.agentsPackNote + ")";
		}
		return reason;
	}

	/**
	 * The candidate that was not chosen, for a report that shows what the build weighed.
	 * @return the runner-up, or {@code null} when there was only one candidate
	 */
	public Candidate runnerUp() {
		if (this.agentsPack == null) {
			return null;
		}
		return (this.chosen == this.agentsPack) ? this.single : this.agentsPack;
	}
}
