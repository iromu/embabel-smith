package com.embabel.smith.studio.rest.dto;

/**
 * The extract response: the model-produced business-process description (the generation blueprint)
 * and a structured error. {@code errorCode} is {@code MODEL_NOT_CONFIGURED} when the endpoint is
 * absent, so the UI renders its dedicated banner.
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
