/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
