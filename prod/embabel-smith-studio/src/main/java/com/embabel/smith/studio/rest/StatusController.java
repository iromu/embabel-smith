package com.embabel.smith.studio.rest;

import com.embabel.smith.common.config.ModelEndpointConfig;
import com.embabel.smith.studio.rest.dto.StatusResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Global endpoint status for the studio header (change 06 polish): reports whether a model endpoint
 * is resolvable and which default model it would use, so the UI can show a live readiness indicator
 * instead of each pane discovering the missing endpoint only after a failed run.
 */
@RestController
public class StatusController {

    private final ModelEndpointConfig endpoint;

    public StatusController(ModelEndpointConfig endpoint) {
        this.endpoint = endpoint;
    }

    @GetMapping("/api/status")
    public StatusResponse status() {
        return new StatusResponse(endpoint.isConfigured(),
                endpoint.isConfigured() ? endpoint.defaultLlm() : null,
                endpoint.configError());
    }
}
