/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.innerloop;

import com.embabel.smith.runtime.trace.TraceRecord;

/**
 * One line of the frozen labelled corpus: the trace exactly as the runtime would have
 * written it, the human answer a detection policy is scored against, and the pack facts
 * the product's {@code Detector} receives as arguments rather than as trace fields.
 *
 * <p>
 * The corpus keeps the product's record shape so a policy sees real traffic; the label
 * fields it adds are what make it a confusion matrix. They are the answer, so they are
 * carried here for the scorer and never handed to a policy — see {@link PolicyInput}. The
 * corpus is fixed for the run ({@code evidence/MUTABILITY.md}, fixed artifact
 * {@code trace-corpus}); this class reads it and cannot write it.
 *
 * @param runId the trace's run identifier, which is also the join key to a policy's
 * verdict
 * @param trace the record, with the product's own fields
 * @param label {@code defect} or {@code not_defect}, as a human labelled it
 * @param labelReason why the human labelled it that way
 * @param specContext the sidecar and re-eval facts for the two triggers that are
 * properties of a pack rather than of a run, or null when the line carries none
 * @author Ivan Rodriguez
 */
public record TraceCase(String runId, TraceRecord trace, String label, String labelReason, SpecContext specContext) {

	/** The label that says a line is a genuine defect. */
	public static final String DEFECT = "defect";

	/** The label that says a line is not. */
	public static final String NOT_DEFECT = "not_defect";

	/**
	 * Whether the human answer for this line is "defect".
	 * @return true when the label is {@code defect}
	 */
	public boolean labelledDefect() {
		return DEFECT.equals(this.label);
	}

	/**
	 * The pack-side facts two of the three triggers need. The corpus records them on a
	 * representative line because neither drift nor a below-baseline re-eval is visible
	 * on a trace line at all; that simplification is stated in
	 * {@code corpus/traces/README.md}.
	 *
	 * @param sidecarContentHash the hash the pack's lifecycle sidecar recorded
	 * @param baselinePassRate the pass-rate recorded as the baseline, or null when none
	 * was
	 * @param reevalPassRate the re-evaluated pass-rate, or null when no re-eval was run —
	 * which disables the below-baseline trigger rather than scoring it as zero
	 * @author Ivan Rodriguez
	 */
	public record SpecContext(String sidecarContentHash, Double baselinePassRate, Double reevalPassRate) {
	}

}
