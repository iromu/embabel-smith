package com.embabel.smith.ui;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.smith.common.config.ModelEndpointConfig;
import com.embabel.smith.nlcompile.NlCompileRunner;
import com.embabel.smith.ui.dto.CompileRequest;
import com.embabel.smith.ui.dto.CompileResponse;
import com.embabel.common.ai.model.LlmOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The NL-authoring pane behind the canvas (POC 15): given a natural-language description, compile a
 * draft skill via POC 2's {@link NlCompileRunner} and report its goal shape + {@code DraftQuality}
 * verdict — the "describe a skill in plain language" half of the builder UI. This is the
 * endpoint-gated half of the canvas: it needs a live OpenAI-compatible endpoint, and returns a clear
 * configuration error (not an opaque model error) when none is set.
 */
@RestController
public class NlAuthoringController {

    private final Ai ai;
    private final AgentPlatform platform;
    private final ModelEndpointConfig endpoint;

    @Autowired
    public NlAuthoringController(Ai ai,
                                 AgentPlatform platform,
                                 @Value("${OPENAI_CUSTOM_BASE_URL:}") String baseUrl,
                                 @Value("${OPENAI_CUSTOM_API_KEY:}") String apiKey,
                                 @Value("${OPENAI_CUSTOM_MODELS:}") String models,
                                 @Value("${EMBABEL_MODELS_DEFAULT_LLM:}") String defaultLlm) {
        this.ai = ai;
        this.platform = platform;
        this.endpoint = new ModelEndpointConfig(baseUrl, apiKey, models, defaultLlm);
    }

    /** Compile a description into a draft; a clear error when no endpoint is configured. */
    @PostMapping("/api/compile")
    public CompileResponse compile(@RequestBody CompileRequest request) {
        String description = request.description() == null ? "" : request.description();
        if (description.isBlank()) {
            return CompileResponse.badRequest("No description supplied; describe the skill first.");
        }
        String configError = endpoint.configError();
        if (configError != null) {
            return CompileResponse.noEndpoint(configError);
        }
        return CompileResponse.from(new NlCompileRunner(ai, platform, LlmOptions.withDefaultLlm())
                .compile(request.agentNameOrDefault(), description));
    }
}
