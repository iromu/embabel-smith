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
 * The extract response: the model-produced business-process description (the generation
 * blueprint) and a structured error. {@code errorCode} is {@code MODEL_NOT_CONFIGURED}
 * when the endpoint is absent, so the UI renders its dedicated banner.
 *
 * @param description the model-produced business-process description.
 * @param error the structured error (null on success).
 * @param errorCode the error code ({@code MODEL_NOT_CONFIGURED} when the endpoint is absent).
 * @author Ivan Rodriguez
 */
public record ExtractResponse(String description, String error, String errorCode) {

	public static ExtractResponse of(String description) {
		return new ExtractResponse(description, null, null);
	}

	public static ExtractResponse noEndpoint(String message) {
		return new ExtractResponse(null, message, "MODEL_NOT_CONFIGURED");
	}

	public static ExtractResponse failed(String message) {
		return new ExtractResponse(null, message, null);
	}
}
