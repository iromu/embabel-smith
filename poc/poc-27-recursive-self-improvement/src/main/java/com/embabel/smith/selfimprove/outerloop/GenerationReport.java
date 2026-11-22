/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.outerloop;

/**
 * What one generation produced, reported as a move rather than as a number.
 *
 * <p>
 * The unit of the claim is the generation, so the report has one of these per generation
 * and the run has no single score to quote: "generation 2 improved on generation 1 by
 * +0.167 against a floor of 0.200" is the sentence the POC is for, and a lone best-of-run
 * number would erase which generation earned it and whether it cleared the floor at all.
 *
 * @param generation the one-based generation number
 * @param improverId the improver that ran this generation
 * @param derivedFrom the variant that improver was built from, or null for generation 1
 * @param scoredVariants how many variants reached a score
 * @param bestVariantId the best variant of this generation
 * @param bestScore its frozen score
 * @param improvement that best score paired against the previous generation's best, or
 * against the baseline for generation 1
 * @param modelCalls the metered calls this generation spent
 * @author Ivan Rodriguez
 */
public record GenerationReport(int generation, String improverId, String derivedFrom, int scoredVariants,
		String bestVariantId, double bestScore, Improvement improvement, int modelCalls) {

	/**
	 * The generation's line in the run report.
	 * @return one line stating the move this generation made.
	 */
	public String describe() {
		String lineage = (this.derivedFrom != null) ? ", derived from " + this.derivedFrom : "";
		return "generation %d (improver %s%s): %d variants scored, best %s at %.3f, %s, %d model calls".formatted(
				this.generation, this.improverId, lineage, this.scoredVariants, this.bestVariantId, this.bestScore,
				this.improvement.describe(), this.modelCalls);
	}

}
