/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.improve;

import java.util.List;

import com.embabel.smith.builder.security.ScanReport;

/**
 * The outcome of running the gate chain on a candidate pack: whether it cleared every
 * gate (and may be published), or the stage and reasons it was rejected at. Carries the
 * re-eval score and the security scan so a rejection is recorded with evidence.
 *
 * @param gated whether the candidate cleared every gate
 * @param stage the stage the candidate was rejected at (null on pass)
 * @param reasons the recorded rejection reasons
 * @param score the re-eval pass-rate, or null when not reached
 * @param scan the security scan report, or null when not run
 * @author Ivan Rodriguez
 */
public record GateResult(boolean gated, String stage, List<String> reasons, Double score, ScanReport scan) {

	/** A candidate that cleared every gate (re-eval beat the baseline). */
	public static GateResult pass(double score, ScanReport scan) {
		return new GateResult(true, null, List.of(), score, scan);
	}

	/** A candidate rejected at a named stage, with the recorded reasons. */
	public static GateResult reject(String stage, List<String> reasons, Double score, ScanReport scan) {
		return new GateResult(false, stage, List.copyOf(reasons), score, scan);
	}
}
