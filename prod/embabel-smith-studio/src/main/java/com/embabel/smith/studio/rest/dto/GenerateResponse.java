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

import com.embabel.smith.common.model.AgentSpec;

/**
 * The generate response: the produced {@link AgentSpec} (serialisable to the frontend's
 * ComposePack shape), the flow-soundness issues (empty when sound), and a structured
 * error. {@code errorCode} is {@code MODEL_NOT_CONFIGURED} when the endpoint is absent,
 * so the UI renders its dedicated banner.
 *
 * @param pack the produced agent spec (null on failure).
 * @param issues the flow-soundness issues (empty when sound).
 * @param error the structured error (null on success).
 * @param errorCode the error code ({@code MODEL_NOT_CONFIGURED} when the endpoint is absent).
 * @author Ivan Rodriguez
 */
public record GenerateResponse(AgentSpec pack, List<String> issues, String error, String errorCode) {

	public static GenerateResponse of(AgentSpec pack, List<String> issues) {
		return new GenerateResponse(pack, issues, null, null);
	}

	public static GenerateResponse noEndpoint(String message) {
		return new GenerateResponse(null, null, message, "MODEL_NOT_CONFIGURED");
	}

	public static GenerateResponse failed(String message) {
		return new GenerateResponse(null, null, message, null);
	}
}
