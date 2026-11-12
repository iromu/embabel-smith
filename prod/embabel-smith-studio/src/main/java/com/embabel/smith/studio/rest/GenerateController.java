package com.embabel.smith.studio.rest;

import com.embabel.smith.common.config.ModelEndpointConfig;
import com.embabel.smith.studio.generate.BlueprintLoader;
import com.embabel.smith.studio.rest.dto.GenerateRequest;
import com.embabel.smith.studio.rest.dto.GenerateResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Author-a-Process workspace's generation step: a blueprint (a description, or a path to
 * business-process docs) in, a generated agent spec (GOAP goal + action steps + types) out —
 * not published, so the UI can render and edit it before publishing via {@code /api/compose}.
 * Endpoint-gated: without a model endpoint it returns the structured {@code MODEL_NOT_CONFIGURED}
 * error. The model-dependent generation is behind the {@link GenerateFn} seam so the endpoint-present
 * path is stub-testable.
 */
@RestController
public class GenerateController {

    /** The model-dependent generation: a blueprint to a generated pack (plus flow issues). */
    @FunctionalInterface
    public interface GenerateFn {
        GenerateResponse generate(String blueprint);
    }

    private final GenerateFn generator;
    private final ModelEndpointConfig endpoint;
    private final BlueprintLoader loader;

    public GenerateController(GenerateFn generator, ModelEndpointConfig endpoint, BlueprintLoader loader) {
        this.generator = generator;
        this.endpoint = endpoint;
        this.loader = loader;
    }

    @PostMapping("/api/generate")
    public GenerateResponse generate(@RequestBody GenerateRequest request) {
        String configError = endpoint.configError();
        if (configError != null) {
            return GenerateResponse.noEndpoint(configError);
        }
        String blueprint;
        try {
            blueprint = loader.resolve(request.blueprint(), request.path());
        } catch (RuntimeException e) {
            return GenerateResponse.failed(e.getMessage() == null ? e.toString() : e.getMessage());
        }
        if (blueprint == null || blueprint.isBlank()) {
            return GenerateResponse.failed(
                    "No blueprint: provide a description or a path to business-process docs.");
        }
        return generator.generate(blueprint);
    }
}
