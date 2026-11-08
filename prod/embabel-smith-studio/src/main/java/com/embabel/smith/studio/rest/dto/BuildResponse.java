package com.embabel.smith.studio.rest.dto;

import com.embabel.smith.builder.BuildResult;

import java.util.List;

/**
 * The build report (the Author pane): whether the pack was published or the build was blocked, at
 * which stage it stopped, and the evidence (eval score, security scan, lifecycle, publish
 * location). A blocked build names the stage and the reasons; a missing endpoint is a structured
 * {@code MODEL_NOT_CONFIGURED} error.
 */
public record BuildResponse(String status, String stage, String packName, Double score,
                            List<String> issues, ScanInfo scan, LifecycleInfo lifecycle,
                            PublishInfo published, String errorCode, String error) {

    public static BuildResponse of(BuildResult result) {
        return new BuildResponse(
                result.status().name(),
                result.stage(),
                result.packName(),
                result.score(),
                result.issues(),
                result.scan() == null ? null : ScanInfo.of(result.scan()),
                result.lifecycle() == null ? null : LifecycleInfo.of(result.lifecycle()),
                result.published() == null ? null : PublishInfo.of(result.published()),
                null,
                null);
    }

    public static BuildResponse blocked(String stage, String packName, List<String> issues, String error) {
        return new BuildResponse("BLOCKED", stage, packName, null, issues, null, null, null, null, error);
    }

    /** The structured "no model endpoint" error. */
    public static BuildResponse noEndpoint(String message) {
        return new BuildResponse("BLOCKED", "config", null, null, List.of(), null, null, null,
                "MODEL_NOT_CONFIGURED", message);
    }
}
