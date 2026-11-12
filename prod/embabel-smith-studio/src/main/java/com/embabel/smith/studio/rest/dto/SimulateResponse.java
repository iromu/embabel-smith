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

import com.embabel.smith.runtime.AgentSpecRunService;
import com.embabel.smith.runtime.trace.TraceRecord;

/**
 * The simulate response: the run outcome plus the per-step trace, so the UI can show the
 * generated flow executing step by step. {@code errorCode} is
 * {@code MODEL_NOT_CONFIGURED} when the endpoint is absent.
 *
 * @param success whether the run succeeded.
 * @param output the goal output (null on failure).
 * @param error the failure reason where present.
 * @param errorCode the error code ({@code MODEL_NOT_CONFIGURED} when the endpoint is absent).
 * @param steps the per-step trace.
 * @author Ivan Rodriguez
 */
public record SimulateResponse(boolean success, String output, String error, String errorCode, List<Step> steps) {

	public static SimulateResponse of(AgentSpecRunService.RunResult result) {
		TraceRecord trace = result.trace();
		List<Step> steps = (trace == null || trace.steps() == null) ? List.of()
				: trace.steps()
					.stream()
					.map((s) -> new Step(s.name(), s.status(), s.durationMs(), s.outputExcerpt()))
					.toList();
		return new SimulateResponse(result.success(), result.output(), result.error(), null, steps);
	}

	public static SimulateResponse noEndpoint(String message) {
		return new SimulateResponse(false, null, message, "MODEL_NOT_CONFIGURED", List.of());
	}

	public static SimulateResponse failed(String message) {
		return new SimulateResponse(false, null, message, null, List.of());
	}

	/**
	 * One executed step: name, status, duration, and a short output excerpt.
	 *
	 * @param name the step name.
	 * @param status the step status.
	 * @param durationMs the step duration in milliseconds.
	 * @param outputExcerpt a short excerpt of the step output.
	 */
	public record Step(String name, String status, long durationMs, String outputExcerpt) {
	}
}
