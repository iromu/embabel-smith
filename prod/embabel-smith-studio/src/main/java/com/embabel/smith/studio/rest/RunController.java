package com.embabel.smith.studio.rest;

import com.embabel.smith.common.config.ModelEndpointConfig;
import com.embabel.smith.runtime.AgentSpecRunService;
import com.embabel.smith.runtime.core.TrustTier;
import com.embabel.smith.studio.rest.dto.RunRequest;
import com.embabel.smith.studio.rest.dto.RunResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Run pane (change 06): pick a source, an exported goal, and an input, and run the goal through
 * the change-03 runtime core. Endpoint-gated — without a configured model endpoint it returns the
 * structured {@code MODEL_NOT_CONFIGURED} error before any run. The run is behind the
 * {@link RunFn} seam so the endpoint-present path is testable with a stub.
 */
@RestController
public class RunController {

    /** The model-dependent run: source + goal + input + planner + tier to a run result. */
    @FunctionalInterface
    public interface RunFn {
        AgentSpecRunService.RunResult run(String source, String goal, String input, String planner,
                                      TrustTier tier);
    }

    private final RunFn runner;
    private final ModelEndpointConfig endpoint;

    public RunController(RunFn runner, ModelEndpointConfig endpoint) {
        this.runner = runner;
        this.endpoint = endpoint;
    }

    @PostMapping("/api/run")
    public RunResponse run(@RequestBody RunRequest request) {
        if (request.source() == null || request.source().isBlank()) {
            return RunResponse.error("No source selected; pick a source to run from.");
        }
        String configError = endpoint.configError();
        if (configError != null) {
            return RunResponse.noEndpoint(configError);
        }
        TrustTier tier = parseTier(request.trustTier());
        try {
            AgentSpecRunService.RunResult result = runner.run(request.source(), request.goal(),
                    request.input(), request.planner(), tier);
            String runId = result.trace() == null ? null : result.trace().runId();
            return result.success()
                    ? RunResponse.ok(result.output(), runId)
                    : RunResponse.failed(result.error(), runId);
        } catch (Exception e) {
            return RunResponse.error("Run failed: " + message(e));
        }
    }

    private static TrustTier parseTier(String tier) {
        if (tier == null || tier.isBlank()) {
            return TrustTier.STANDARD;
        }
        try {
            return TrustTier.valueOf(tier.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return TrustTier.STANDARD;
        }
    }

    private static String message(Exception e) {
        return e.getMessage() == null ? e.toString() : e.getMessage();
    }
}
