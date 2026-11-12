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

package com.embabel.smith.unified.dto;

import java.util.List;

/**
 * The result of the eval/refinement pane (POC 9): the composite score before (and, when a
 * prompt rewrite ran, after) refinement, the step refined, the revised prompt, and the
 * inputs that failed. {@link #error} is non-null when the request could not run (no spec
 * selected, an unknown spec, or no endpoint configured) — in which case the
 * score/refinement fields are empty.
 *
 * @param error The error when the request could not run, or null.
 * @param specName The spec evaluated.
 * @param stepName The step refined.
 * @param beforeScore The composite score before refinement.
 * @param afterScore The composite score after refinement.
 * @param refined Whether a prompt rewrite ran.
 * @param improved Whether the score improved.
 * @param revisedPrompt The revised prompt, when a rewrite ran.
 * @param failingInputs The inputs that failed.
 * @author Ivan Rodriguez
 */
public record EvalResponse(String error, String specName, String stepName, double beforeScore, double afterScore,
		boolean refined, boolean improved, String revisedPrompt, List<String> failingInputs) {

	public static EvalResponse error(String error) {
		return new EvalResponse(error, null, null, 0.0, 0.0, false, false, null, List.of());
	}

	public static EvalResponse ok(String specName, String stepName, double before, double after, boolean refined,
			boolean improved, String revisedPrompt, List<String> failingInputs) {
		return new EvalResponse(null, specName, stepName, round4(before), round4(after), refined, improved,
				revisedPrompt, failingInputs);
	}

	private static double round4(double v) {
		return Math.round(v * 10000.0) / 10000.0;
	}
}
