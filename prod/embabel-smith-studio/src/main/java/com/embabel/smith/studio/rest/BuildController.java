package com.embabel.smith.studio.rest;

import com.embabel.smith.builder.BuildOptions;
import com.embabel.smith.builder.BuildResult;
import com.embabel.smith.builder.publish.PublishTarget;
import com.embabel.smith.common.config.ModelEndpointConfig;
import com.embabel.smith.studio.rest.dto.BuildRequest;
import com.embabel.smith.studio.rest.dto.BuildResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Author pane (change 06): submit a description and an output source, and build the agent spec
 * through the change-04 builder core (compile → eval → security → lifecycle → publish). Endpoint-
 * gated — without a configured model endpoint it returns the structured
 * {@code MODEL_NOT_CONFIGURED} error before any build. The build is behind the {@link BuildFn} seam
 * so the endpoint-present path is testable with a stub.
 */
@RestController
public class BuildController {

    /** The model-dependent build: description + output source + options to a build result. */
    @FunctionalInterface
    public interface BuildFn {
        BuildResult build(String description, PublishTarget target, BuildOptions options);
    }

    private final BuildFn builder;
    private final ModelEndpointConfig endpoint;

    public BuildController(BuildFn builder, ModelEndpointConfig endpoint) {
        this.builder = builder;
        this.endpoint = endpoint;
    }

    @PostMapping("/api/build")
    public BuildResponse build(@RequestBody BuildRequest request) {
        if (request.description() == null || request.description().isBlank()) {
            return BuildResponse.blocked("input", null,
                    java.util.List.of("No description provided; describe the spec to build."),
                    "No description provided; describe the spec to build.");
        }
        if (request.outputSource() == null || request.outputSource().isBlank()) {
            return BuildResponse.blocked("input", null,
                    java.util.List.of("No output source provided; pick where to publish the pack."),
                    "No output source provided; pick where to publish the pack.");
        }
        String configError = endpoint.configError();
        if (configError != null) {
            return BuildResponse.noEndpoint(configError);
        }
        try {
            PublishTarget target = PublishTarget.parse(request.outputSource());
            BuildOptions options = new BuildOptions(
                    request.maxRefine() == null ? BuildOptions.DEFAULT_MAX_REFINE : request.maxRefine(),
                    BuildOptions.DEFAULT_SCORE_GATE,
                    request.overwrite() != null && request.overwrite(),
                    request.push() != null && request.push());
            return BuildResponse.of(builder.build(request.description(), target, options));
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.toString() : e.getMessage();
            return BuildResponse.blocked("build", null, java.util.List.of(message), message);
        }
    }
}
