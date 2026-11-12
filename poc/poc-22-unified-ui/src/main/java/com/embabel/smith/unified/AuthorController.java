package com.embabel.smith.unified;

import com.embabel.smith.common.config.ModelEndpointConfig;
import com.embabel.smith.unified.dto.AuthorDraft;
import com.embabel.smith.unified.dto.CompileRequest;
import com.embabel.smith.unified.dto.CompileResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Author pane (POC 22): given a natural-language description, compile a draft spec via POC 2's
 * {@link com.embabel.smith.nlcompile.NlCompileRunner} and report its goal shape +
 * {@code DraftQuality} verdict — the "describe a spec in plain language" half of the builder. This is
 * endpoint-gated: it needs a live OpenAI-compatible endpoint, and returns a clear configuration error
 * (not an opaque model error) when none is set. The model call is behind the {@link CompileFn} seam so
 * the endpoint-present path is testable with a stub.
 */
@RestController
public class AuthorController {

    /** The model-dependent compile: agent name + description to the mapped {@link AuthorDraft}. */
    @FunctionalInterface
    public interface CompileFn {
        AuthorDraft compile(String agentName, String description) throws Exception;
    }

    private final CompileFn compiler;
    private final ModelEndpointConfig endpoint;

    public AuthorController(CompileFn compiler, ModelEndpointConfig endpoint) {
        this.compiler = compiler;
        this.endpoint = endpoint;
    }

    /** Compile a description into a draft; a clear error when no endpoint is configured. */
    @PostMapping("/api/compile")
    public CompileResponse compile(@RequestBody CompileRequest request) {
        String description = request.description() == null ? "" : request.description();
        if (description.isBlank()) {
            return CompileResponse.badRequest("No description supplied; describe the spec first.");
        }
        String configError = endpoint.configError();
        if (configError != null) {
            return CompileResponse.noEndpoint(configError);
        }
        try {
            AuthorDraft draft = compiler.compile(request.agentNameOrDefault(), description);
            return CompileResponse.ok(draft);
        } catch (Exception e) {
            return CompileResponse.error("Compile failed: "
                    + (e.getMessage() == null ? e.toString() : e.getMessage()));
        }
    }
}
