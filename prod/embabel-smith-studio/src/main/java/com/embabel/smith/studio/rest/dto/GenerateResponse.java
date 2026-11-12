package com.embabel.smith.studio.rest.dto;

import com.embabel.smith.common.model.AgentSpec;

import java.util.List;

/**
 * The generate response: the produced {@link AgentSpec} (serialisable to the frontend's ComposePack
 * shape), the flow-soundness issues (empty when sound), and a structured error. {@code errorCode}
 * is {@code MODEL_NOT_CONFIGURED} when the endpoint is absent, so the UI renders its dedicated
 * banner.
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
