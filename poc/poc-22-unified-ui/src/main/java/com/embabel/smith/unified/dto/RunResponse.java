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

/**
 * The result of a run (POC 1): the goal output, or a clear error. {@link #error} is
 * non-null when the run could not happen (no spec selected, an unknown spec, no endpoint
 * configured, or the plan failed) — in which case {@link #output} is null.
 *
 * @param error The error when the run could not happen, or null.
 * @param output The goal output, or null when the run failed.
 * @author Ivan Rodriguez
 */
public record RunResponse(String error, String output) {

	public static RunResponse ok(String output) {
		return new RunResponse(null, output);
	}

	public static RunResponse error(String error) {
		return new RunResponse(error, null);
	}

	public static RunResponse noEndpoint(String error) {
		return new RunResponse(error, null);
	}
}
