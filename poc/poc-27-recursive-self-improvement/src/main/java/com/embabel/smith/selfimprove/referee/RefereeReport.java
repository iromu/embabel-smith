/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.referee;

import java.util.List;

/**
 * What the referee recorded about one candidate. Two scores are carried rather than one,
 * because two checks were run: {@code gateScore} is the product's gate scoring the task
 * set it was given (its scorer, which tests one substring per task), and
 * {@code frozenScore} is this POC scoring the same answers with the full deterministic
 * check the task files declare. Where the two disagree the record shows both, so a
 * narrowing in the conversion is visible instead of being absorbed into a single number.
 *
 * @param candidate the variant name
 * @param gated whether the product's gate let the candidate through
 * @param gateStage the stage the gate rejected at, null when it passed
 * @param gateReasons the reasons the gate recorded
 * @param gateScore the gate's re-eval pass-rate, null when the gate never reached re-eval
 * @param frozenScore the pass-rate under the frozen set's full check
 * @param frozenFailedTaskIds the frozen tasks the candidate did not pass
 * @param scoredTaskIds the identifiers the candidate was actually scored on, which are
 * the frozen identifiers and nothing else
 * @param excludedProposedTaskIds the identifiers of evaluation tasks the candidate
 * proposed and the referee excluded from scoring
 * @author Ivan Rodriguez
 */
public record RefereeReport(String candidate, boolean gated, String gateStage, List<String> gateReasons,
		Double gateScore, double frozenScore, List<String> frozenFailedTaskIds, List<String> scoredTaskIds,
		List<String> excludedProposedTaskIds) {

	public RefereeReport {
		gateReasons = List.copyOf(gateReasons);
		frozenFailedTaskIds = List.copyOf(frozenFailedTaskIds);
		scoredTaskIds = List.copyOf(scoredTaskIds);
		excludedProposedTaskIds = List.copyOf(excludedProposedTaskIds);
	}

}
