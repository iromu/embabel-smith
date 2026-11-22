/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.innerloop;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One line of the per-generation record the inner loop writes.
 *
 * <p>
 * It carries the two numbers separately, because the spec forbids blending them, and it
 * carries a measured model-call count rather than an asserted one: {@link ModelCallMeter}
 * is the only place a call could be spent, and the number here is the delta that counter
 * moved while this generation ran. A zero that is read from a counter is a finding; a
 * zero that is typed into a literal is a hope.
 *
 * <p>
 * A generation that reached for a fixed artifact is recorded with no score at all. The
 * spec's remedy for that case is to name the artifact and record nothing for the
 * generation, and a score sitting next to the rejection would be exactly the
 * accepted-and-scored outcome it rules out.
 *
 * @param index the generation's position in the run, from zero
 * @param variantId this generation's variant
 * @param parentId the variant it was derived from
 * @param operator the one mutation applied, or {@code SEED}
 * @param status {@code scored}, {@code failed} or {@code rejected}
 * @param tuning the score on the split it is fitted against, or null when nothing was
 * scored
 * @param heldout the score on the split it is judged on, or null when nothing was scored
 * @param diagnostic the engine's words for a generation that produced no score, or null
 * @param rejectedFixedArtifact the fixed artifact a rejection reached for, or null
 * @param modelCalls the calls spent during this generation, read from the meter
 * @param durationMillis how long the generation's runs took
 * @author Ivan Rodriguez
 */
public record GenerationRecord(int index, String variantId, String parentId, String operator, String status,
		SplitScore tuning, SplitScore heldout, String diagnostic, String rejectedFixedArtifact, int modelCalls,
		long durationMillis) {

	/** The generation ran, scored, and is in the archive. */
	public static final String SCORED = "scored";

	/**
	 * The generation produced no verdicts — a syntax error, a non-zero exit, a bad
	 * answer.
	 */
	public static final String FAILED = "failed";

	/** The generation reached outside its sandbox and is recorded without a score. */
	public static final String REJECTED = "rejected";

	/**
	 * The artifact that says what a generation may not touch. A rejection names the fixed
	 * artifact and points here rather than restating the boundary.
	 */
	public static final String BOUNDARY = "evidence/MUTABILITY.md";

	/**
	 * A scored generation.
	 * @param variant the variant that ran
	 * @param tuning its score on the tuning split
	 * @param heldout its score on the held-out split
	 * @param modelCalls the calls the meter counted during it
	 * @param durationMillis the measured run time
	 * @return the record.
	 */
	public static GenerationRecord scored(PolicyGenerator.Variant variant, SplitScore tuning, SplitScore heldout,
			int modelCalls, long durationMillis) {
		return new GenerationRecord(variant.index(), variant.variantId(), variant.parentId(), variant.operator().name(),
				SCORED, tuning, heldout, null, null, modelCalls, durationMillis);
	}

	/**
	 * A generation that ran and produced nothing to score. It is retained like every
	 * other variant; a collapsed generation has to be visible as a regression rather than
	 * as an absence.
	 * @param variant the variant that ran
	 * @param diagnostic the engine's explanation
	 * @param modelCalls the calls the meter counted during it
	 * @param durationMillis the measured run time
	 * @return the record.
	 */
	public static GenerationRecord failed(PolicyGenerator.Variant variant, String diagnostic, int modelCalls,
			long durationMillis) {
		return new GenerationRecord(variant.index(), variant.variantId(), variant.parentId(), variant.operator().name(),
				FAILED, null, null, diagnostic, null, modelCalls, durationMillis);
	}

	/**
	 * A generation that reached for something the run declared fixed.
	 * @param variant the variant that ran
	 * @param fixedArtifact the artifact it tried to reach, named as {@code MUTABILITY.md}
	 * names it
	 * @param diagnostic the denial that caused the rejection
	 * @param modelCalls the calls the meter counted during it
	 * @param durationMillis the measured run time
	 * @return the record, with no score on it.
	 */
	public static GenerationRecord rejected(PolicyGenerator.Variant variant, String fixedArtifact, String diagnostic,
			int modelCalls, long durationMillis) {
		return new GenerationRecord(variant.index(), variant.variantId(), variant.parentId(), variant.operator().name(),
				REJECTED, null, null, diagnostic, fixedArtifact, modelCalls, durationMillis);
	}

	/**
	 * Whether this generation improved on a given best. The comparison is lexicographic —
	 * precision first, then recall, then the variant name so a tie cannot depend on
	 * iteration order. Selection needs one ordering; the report keeps two numbers, and
	 * the ban the spec states is on blending the reported score, not on choosing a
	 * favourite.
	 * @param best the current best scored generation, or null when nothing has scored yet
	 * @return true when this generation is strictly better.
	 */
	public boolean beats(GenerationRecord best) {
		if (this.tuning == null) {
			return false;
		}
		if (best == null || best.tuning == null) {
			return true;
		}
		if (this.tuning.precision() != best.tuning.precision()) {
			return this.tuning.precision() > best.tuning.precision();
		}
		if (this.tuning.recall() != best.tuning.recall()) {
			return this.tuning.recall() > best.tuning.recall();
		}
		return this.variantId.compareTo(best.variantId) < 0;
	}

	/**
	 * The record as a JSON object, for the per-generation file. Nulls are written rather
	 * than dropped, so a reader can tell "no score was recorded" from "the field was
	 * forgotten".
	 * @return the fields, in a stable order.
	 */
	public Map<String, Object> asMap() {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("generation", this.index);
		out.put("variant", this.variantId);
		out.put("parent", this.parentId);
		out.put("operator", this.operator);
		out.put("status", this.status);
		out.put("tuning", (this.tuning != null) ? scoreMap(this.tuning) : null);
		out.put("heldout", (this.heldout != null) ? scoreMap(this.heldout) : null);
		out.put("model_calls", this.modelCalls);
		out.put("duration_ms", this.durationMillis);
		if (this.diagnostic != null) {
			out.put("diagnostic", this.diagnostic);
		}
		if (this.rejectedFixedArtifact != null) {
			out.put("rejected_fixed_artifact", this.rejectedFixedArtifact);
			out.put("boundary", BOUNDARY);
			out.put("rejection_reason",
					"the generation tried to reach the fixed artifact '" + this.rejectedFixedArtifact + "' named in "
							+ BOUNDARY + "; the engine denied it: " + this.diagnostic);
		}
		return out;
	}

	private static Map<String, Object> scoreMap(SplitScore score) {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("split", score.split());
		out.put("cases", score.cases());
		out.put("precision", score.precision());
		out.put("recall", score.recall());
		out.put("true_positives", score.truePositives());
		out.put("false_positives", score.falsePositives());
		out.put("false_negatives", score.falseNegatives());
		out.put("true_negatives", score.trueNegatives());
		return out;
	}

}
