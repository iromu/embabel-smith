package com.embabel.smith.studio.rest;

import com.embabel.smith.common.config.ModelEndpointConfig;
import com.embabel.smith.studio.discover.BusinessProcessDiscovery;
import com.embabel.smith.studio.rest.dto.ExtractRequest;
import com.embabel.smith.studio.rest.dto.ExtractResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * The workspace's model-based process extraction: load the business-process documents from a docs
 * path or a repository URL and run the discovery prompt over them, returning a business-process
 * description that the UI hands to generation as its blueprint. Endpoint-gated — the extraction
 * runs the model. The model-dependent call is behind the {@link ExtractFn} seam so the
 * endpoint-present path is stub-testable.
 */
@RestController
public class ExtractController {

    /** The model-dependent extraction: a docs corpus to a business-process description. */
    @FunctionalInterface
    public interface ExtractFn {
        String extract(String corpus);
    }

    private final ExtractFn extractor;
    private final ModelEndpointConfig endpoint;
    private final BusinessProcessDiscovery discovery;

    public ExtractController(ExtractFn extractor, ModelEndpointConfig endpoint,
                             BusinessProcessDiscovery discovery) {
        this.extractor = extractor;
        this.endpoint = endpoint;
        this.discovery = discovery;
    }

    @PostMapping("/api/extract")
    public ExtractResponse extract(@RequestBody ExtractRequest request) {
        String configError = endpoint.configError();
        if (configError != null) {
            return ExtractResponse.noEndpoint(configError);
        }
        String corpus;
        try {
            BusinessProcessDiscovery.DiscoverResult result = discovery.load(request.path(), request.url());
            corpus = toCorpus(result.docs());
        } catch (RuntimeException e) {
            return ExtractResponse.failed(e.getMessage() == null ? e.toString() : e.getMessage());
        }
        if (corpus == null || corpus.isBlank()) {
            return ExtractResponse.failed("No documents to extract a process from.");
        }
        try {
            return ExtractResponse.of(extractor.extract(corpus));
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.toString() : e.getMessage();
            return ExtractResponse.failed("Extraction failed: " + message);
        }
    }

    /** Concatenates the loaded docs into one corpus, mirroring the generation blueprint shape. */
    private static String toCorpus(Map<String, String> docs) {
        StringBuilder sb = new StringBuilder();
        docs.forEach((name, body) ->
                sb.append("--- ").append(name).append(" ---\n").append(body).append("\n\n"));
        return sb.toString().strip();
    }
}
