/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.outerloop;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One scored variant, as the archive keeps it: what it scored, what it descended from,
 * and what it cost to find out.
 *
 * <p>
 * All three are required by the run's own claims. Score without parent is a leaderboard,
 * not a lineage, and the archive's point is that a variant scoring below its parent stays
 * reachable <em>as its parent's child</em>. Score and parent without cost is a run that
 * cannot say what its result was worth.
 *
 * <p>
 * Two scores are kept rather than one. {@code frozenScore} is the deterministic full
 * check over the frozen task set and is the currency improvement is judged in;
 * {@code gateScore} is the product gate's narrower re-eval number, recorded next to the
 * stage it stopped at so a disagreement between the two is visible instead of averaged
 * away.
 *
 * @param generation the generation that produced it
 * @param variantId this variant's identifier
 * @param parentVariantId the variant it was derived from, or null for a root
 * @param improverId the improver that proposed it
 * @param operator the mutation that produced it, or {@code SEED} for an unchanged policy
 * @param gated whether the product's gate let it through
 * @param gateStage the stage the gate stopped at
 * @param gateScore the gate's own score, or null when it never scored the candidate
 * @param frozenScore the deterministic full check over the frozen task set
 * @param improvement this variant's score read against the score it is paired with
 * @param modelCalls the metered model calls this variant cost
 * @param durationMillis how long scoring it took
 * @author Ivan Rodriguez
 */
public record VariantRecord(int generation, String variantId, String parentVariantId, String improverId,
		String operator, boolean gated, String gateStage, Double gateScore, double frozenScore, Improvement improvement,
		int modelCalls, long durationMillis) {

	/**
	 * The record as a flat map, for the JSONL the run writes.
	 * @return the fields, in report order.
	 */
	public Map<String, Object> asMap() {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("generation", this.generation);
		out.put("variantId", this.variantId);
		out.put("parentVariantId", this.parentVariantId);
		out.put("improverId", this.improverId);
		out.put("operator", this.operator);
		out.put("gated", this.gated);
		out.put("gateStage", this.gateStage);
		out.put("gateScore", this.gateScore);
		out.put("frozenScore", this.frozenScore);
		out.put("delta", this.improvement.delta());
		out.put("verdict", this.improvement.verdict());
		out.put("modelCalls", this.modelCalls);
		out.put("durationMillis", this.durationMillis);
		return out;
	}

	/**
	 * One-line form for the run report.
	 * @return the variant, its score, its parent and its cost.
	 */
	public String describe() {
		return "g%d %-42s frozen=%.3f %s parent=%s calls=%d gate=%s".formatted(this.generation, this.variantId,
				this.frozenScore, this.improvement.verdict(), this.parentVariantId, this.modelCalls, this.gateStage);
	}

}
