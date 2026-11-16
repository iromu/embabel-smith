/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.core;

/**
 * The choice between the two candidates a build generated from one description.
 *
 * <p>
 * Decomposing a process is the better answer only when it is both sound and measurably
 * better: the process has to validate its cross-agent hand-offs and out-score the
 * single-agent spec. Anything else — an invalid process, a missing candidate, a tie —
 * recommends the single-agent spec, because that candidate is the one whose soundness the
 * single-pack validator already vouched for.
 *
 * @author Ivan Rodriguez
 */
public final class CandidateRecommender {

	private CandidateRecommender() {
	}

	/**
	 * The candidate the build should publish.
	 * @param single the single-agent candidate
	 * @param process the multi-agent candidate, or {@code null} when none was generated
	 * @return the recommended candidate
	 */
	public static Candidate recommend(Candidate single, Candidate process) {
		boolean processWins = process != null && process.valid() && process.score() != null && single.score() != null
				&& process.score() > single.score();
		return processWins ? process : single;
	}

	/**
	 * Why a candidate was recommended, for the build report.
	 * @param single the single-agent candidate
	 * @param process the multi-agent candidate, or {@code null} when none was generated
	 * @param chosen the recommended candidate
	 * @return one line naming the scores that decided it
	 */
	public static String reason(Candidate single, Candidate process, Candidate chosen) {
		if (chosen.shape() == CandidateShape.MULTI_AGENT) {
			return "multi-agent process out-scored the single-agent spec (" + format(process.score()) + " vs "
					+ format(single.score()) + ")";
		}
		if (process == null) {
			return chosen.shape().label() + " chosen; the model produced no multi-agent candidate";
		}
		if (!process.valid()) {
			return chosen.shape().label() + " chosen; the multi-agent process '" + process.name()
					+ "' did not validate";
		}
		if (process.score() != null && process.score().equals(single.score())) {
			return chosen.shape().label() + " chosen on the tie-break (" + format(single.score()) + " each)";
		}
		return chosen.shape().label() + " chosen; " + format(single.score()) + " vs " + format(process.score())
				+ " for the multi-agent process";
	}

	private static String format(Double score) {
		return (null != score) ? String.format("%.3f", score) : "not scored";
	}

}
