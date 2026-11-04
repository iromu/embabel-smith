package com.embabel.smith.unified;

import com.embabel.smith.common.config.ModelEndpointConfig;
import com.embabel.smith.unified.dto.RunRequest;
import com.embabel.smith.unified.dto.RunResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Run pane (POC 22): pick a skill from the palette and run the fixed no-code runtime (POC 1's
 * {@link com.embabel.smith.runtime.SkillRunner}) over its exported goal — the action bodies invoke the
 * model, so this is endpoint-gated. Without a configured endpoint it returns a clear, named
 * configuration error before any model call. The model call is behind the {@link RunFn} seam so the
 * endpoint-present path is testable with a stub.
 */
@RestController
public class RunController {

    /** The model-dependent run: skill name + input to the goal output. */
    @FunctionalInterface
    public interface RunFn {
        String run(String skillName, String input) throws Exception;
    }

    private final SkillCatalog catalog;
    private final RunFn runner;
    private final ModelEndpointConfig endpoint;

    public RunController(SkillCatalog catalog, RunFn runner, ModelEndpointConfig endpoint) {
        this.catalog = catalog;
        this.runner = runner;
        this.endpoint = endpoint;
    }

    /** Run a skill over an input; a clear error when no endpoint is configured. */
    @PostMapping("/api/run")
    public RunResponse run(@RequestBody RunRequest request) {
        String skillName = request.skillName() == null ? "" : request.skillName();
        if (skillName.isBlank()) {
            return RunResponse.error("No skill selected; pick a skill to run first.");
        }
        if (catalog.get(skillName) == null) {
            return RunResponse.error("Unknown skill: " + skillName + ". Choose one from the palette.");
        }
        String configError = endpoint.configError();
        if (configError != null) {
            return RunResponse.noEndpoint(configError);
        }
        try {
            String output = runner.run(skillName, request.inputOrDefault());
            return RunResponse.ok(output);
        } catch (Exception e) {
            return RunResponse.error("Run failed: " + (e.getMessage() == null ? e.toString() : e.getMessage()));
        }
    }
}
