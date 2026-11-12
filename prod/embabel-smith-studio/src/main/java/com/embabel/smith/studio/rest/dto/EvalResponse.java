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

package com.embabel.smith.studio.rest.dto;

import java.util.List;

/**
 * The outcome of evaluating a spec (the Eval pane): the final pass-rate score, how many
 * prompt rewrites were applied, the tasks still failing, whether the gate was met, and
 * the revised terminal prompt when a rewrite was applied. A missing endpoint is a
 * structured {@code MODEL_NOT_CONFIGURED} error.
 *
 * @param error the structured error (null on success).
 * @param errorCode the error code ({@code MODEL_NOT_CONFIGURED} when the endpoint is absent).
 * @param spec the spec that was evaluated.
 * @param score the final pass-rate score.
 * @param refinementCount how many prompt rewrites were applied.
 * @param failingTasks the tasks still failing.
 * @param passed whether the gate was met.
 * @param revisedPrompt the revised terminal prompt when a rewrite was applied.
 * @author Ivan Rodriguez
 */
public record EvalResponse(String error, String errorCode, String spec, Double score, int refinementCount,
		List<String> failingTasks, boolean passed, String revisedPrompt) {

	public static EvalResponse ok(String spec, double score, int refinementCount, List<String> failingTasks,
			boolean passed, String revisedPrompt) {
		return new EvalResponse(null, null, spec, score, refinementCount, failingTasks, passed, revisedPrompt);
	}

	public static EvalResponse error(String error) {
		return new EvalResponse(error, null, null, null, 0, List.of(), false, null);
	}

	/** The structured "no model endpoint" error. */
	public static EvalResponse noEndpoint(String message) {
		return new EvalResponse(message, "MODEL_NOT_CONFIGURED", null, null, 0, List.of(), false, null);
	}
}
