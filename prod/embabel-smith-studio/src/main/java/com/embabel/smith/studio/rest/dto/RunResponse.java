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
 * The outcome of a run: success, the goal output (or the failure reason), the run id (so
 * the run is findable in the Traces pane), and a structured error code when the run could
 * not proceed.
 *
 * @param success whether the run succeeded.
 * @param output the goal output (null on failure).
 * @param error the failure reason where present.
 * @param errorCode the structured error code when the run could not proceed.
 * @param runId the run id (so the run is findable in the Traces pane).
 * @author Ivan Rodriguez
 */
public record RunResponse(boolean success, String output, String error, String errorCode, String runId) {

	public static RunResponse ok(String output, String runId) {
		return new RunResponse(true, output, null, null, runId);
	}

	public static RunResponse failed(String error, String runId) {
		return new RunResponse(false, null, error, null, runId);
	}

	public static RunResponse error(String error) {
		return new RunResponse(false, null, error, null, null);
	}

	/**
	 * The structured "no model endpoint" error (the endpoint-gated panes render a banner
	 * for it).
	 */
	public static RunResponse noEndpoint(String message) {
		return new RunResponse(false, null, message, "MODEL_NOT_CONFIGURED", null);
	}
}
