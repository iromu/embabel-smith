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

/**
 * One executed step of a run (mirrors {@code TraceRecord.StepRecord}).
 *
 * @param name the step name.
 * @param status the step status.
 * @param durationMs the step duration in milliseconds.
 * @param outputExcerpt a short excerpt of the step output.
 * @author Ivan Rodriguez
 */
public record StepInfo(String name, String status, long durationMs, String outputExcerpt) {

	public static StepInfo of(com.embabel.smith.runtime.trace.TraceRecord.StepRecord step) {
		return new StepInfo(step.name(), step.status(), step.durationMs(), step.outputExcerpt());
	}
}
