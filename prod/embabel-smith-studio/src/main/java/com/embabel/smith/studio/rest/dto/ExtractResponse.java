/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.rest.dto;

/**
 * The extract response: the model-produced business-process description (the generation
 * blueprint) and a structured error. {@code errorCode} is {@code MODEL_NOT_CONFIGURED}
 * when the endpoint is absent, so the UI renders its dedicated banner.
 *
 * @param description the model-produced business-process description.
 * @param error the structured error (null on success).
 * @param errorCode the error code ({@code MODEL_NOT_CONFIGURED} when the endpoint is
 * absent).
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
