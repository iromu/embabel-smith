/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.outerloop;

/**
 * A candidate's score read as a difference against the score of the variant it was
 * derived from, judged against the {@link NoiseFloor} rather than against zero.
 *
 * <p>
 * Paired, not absolute: the number that matters is what this generation's improver
 * changed relative to the thing it was derived from, and a generation report is a list of
 * these rather than a single score. Both sides are frozen scores — the deterministic full
 * check over the frozen task set — so the two sides are in one currency. The gate score
 * the referee also reports is a different, narrower number and is recorded separately;
 * where the two disagree is visible in the per-variant record rather than averaged away
 * here.
 *
 * @param variantId the candidate being judged
 * @param referenceVariantId the variant it is paired with, or null when the reference is
 * the run's baseline rather than an archived variant
 * @param referenceScore the score the candidate is compared against
 * @param score the candidate's score
 * @param floor the noise floor the delta has to clear
 * @author Ivan Rodriguez
 */
public record Improvement(String variantId, String referenceVariantId, double referenceScore, double score,
		double floor) {

	/** The delta clears the floor upward. */
	public static final String IMPROVEMENT = "improvement";

	/** The delta does not clear the floor, in either direction. */
	public static final String NO_IMPROVEMENT = "no improvement";

	/** The delta clears the floor downward. */
	public static final String REGRESSION = "regression";

	/**
	 * Pairs a score against the score it is meant to be read next to.
	 * @param reference the score the candidate is compared against
	 * @param score the candidate's score
	 * @param floor the noise floor to clear
	 * @param variantId the candidate being judged
	 * @param referenceVariantId the variant the reference belongs to, or null for a
	 * baseline reference
	 * @return the paired comparison.
	 */
	public static Improvement paired(double reference, double score, NoiseFloor floor, String variantId,
			String referenceVariantId) {
		return new Improvement(variantId, referenceVariantId, reference, score, floor.delta());
	}

	/**
	 * The move this candidate made over its reference.
	 * @return score minus reference score.
	 */
	public double delta() {
		return this.score - this.referenceScore;
	}

	/**
	 * Whether the move is large enough to count. This is the judgement; the gate agrees
	 * with it by construction, because the run asks the gate to beat a baseline raised by
	 * the same floor.
	 * @return true when the delta clears the floor upward.
	 */
	public boolean accepted() {
		return delta() > this.floor;
	}

	/**
	 * The verdict in words, for the report.
	 * @return one of {@link #IMPROVEMENT}, {@link #NO_IMPROVEMENT}, {@link #REGRESSION}.
	 */
	public String verdict() {
		if (delta() > this.floor) {
			return IMPROVEMENT;
		}
		if (delta() < -this.floor) {
			return REGRESSION;
		}
		return NO_IMPROVEMENT;
	}

	/**
	 * One-line form for the per-generation report line.
	 * @return the delta, the floor and the verdict.
	 */
	public String describe() {
		return String.format("%+.3f vs %+.3f floor -> %s", delta(), this.floor, verdict());
	}

}
