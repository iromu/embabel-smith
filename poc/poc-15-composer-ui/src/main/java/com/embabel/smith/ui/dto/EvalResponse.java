/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.ui.dto;

import java.util.List;

import com.embabel.smith.refinement.RefinementRunner;

/**
 * The result of the eval/refinement pane: the composite score before (and, when a prompt
 * rewrite ran, after) refinement, the step that was refined, the revised prompt, and the
 * inputs that failed. {@link #error} is non-null when the request could not be run (no
 * spec selected, unknown spec, or no endpoint configured) — in which case the
 * score/refinement fields are empty.
 *
 * @param error the error message when the request could not be run
 * @param specName the evaluated spec name
 * @param stepName the step that was refined
 * @param beforeScore the composite score before refinement
 * @param afterScore the composite score after refinement
 * @param refined whether a prompt rewrite ran
 * @param improved whether the score improved
 * @param revisedPrompt the revised prompt
 * @param failingInputs the inputs that failed
 * @author Ivan Rodriguez
 */
public record EvalResponse(String error, String specName, String stepName, double beforeScore, double afterScore,
		boolean refined, boolean improved, String revisedPrompt, List<String> failingInputs) {

	public static EvalResponse error(String error) {
		return new EvalResponse(error, null, null, 0.0, 0.0, false, false, null, List.of());
	}

	public static EvalResponse from(RefinementRunner.RefinementOutcome outcome, String specName, String stepName) {
		return new EvalResponse(null, specName, stepName, round4(outcome.beforeScore()), round4(outcome.afterScore()),
				outcome.revisedPrompt() != null, outcome.improved(), outcome.revisedPrompt(), outcome.failingInputs());
	}

	private static double round4(double v) {
		return Math.round(v * 10000.0) / 10000.0;
	}
}
