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
 * @param process the multi-agent candidate as scored, or {@code null} when none was
 * generated
 * @param processNote what stopped the multi-agent candidate from being published — the
 * generation, repair or validation failure the report should name rather than hide
 * @author Ivan Rodriguez
 */
public record Recommendation(Candidate chosen, Candidate single, Candidate process, String processNote) {

	/**
	 * Applies the recommendation rule to the two candidates.
	 * @param single the single-agent candidate
	 * @param process the multi-agent candidate, or {@code null} when none was generated
	 * @param processNote why there is no scored multi-agent candidate, when there is none
	 * @return the recommendation
	 */
	public static Recommendation choose(Candidate single, Candidate process, String processNote) {
		return new Recommendation(CandidateRecommender.recommend(single, process), single, process, processNote);
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
		String reason = CandidateRecommender.reason(this.single, this.process, this.chosen);
		if (this.processNote != null && !this.processNote.isBlank()) {
			return reason + " (" + this.processNote + ")";
		}
		return reason;
	}

	/**
	 * The candidate that was not chosen, for a report that shows what the build weighed.
	 * @return the runner-up, or {@code null} when there was only one candidate
	 */
	public Candidate runnerUp() {
		if (this.process == null) {
			return null;
		}
		return (this.chosen == this.process) ? this.single : this.process;
	}
}
