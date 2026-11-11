package com.embabel.smith.studio.rest;

import com.embabel.smith.studio.discover.BusinessProcessDiscovery;
import com.embabel.smith.studio.rest.dto.DiscoverRequest;
import com.embabel.smith.studio.rest.dto.DiscoverResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The workspace's discovery/ingest step: load the business-process documents from a local docs
 * path or a repository URL, and return them for presentation. Offline for a local path (no model
 * call); a repository URL is cloned first. The model-based extraction of a raw codebase is a later
 * step.
 */
@RestController
public class DiscoverController {

    private final BusinessProcessDiscovery discovery;

    public DiscoverController(BusinessProcessDiscovery discovery) {
        this.discovery = discovery;
    }

    @PostMapping("/api/discover")
    public DiscoverResponse discover(@RequestBody DiscoverRequest request) {
        try {
            BusinessProcessDiscovery.DiscoverResult result = discovery.load(request.path(), request.url());
            return DiscoverResponse.of(result.sourceType(), result.location(), result.docs());
        } catch (RuntimeException e) {
            return DiscoverResponse.failed(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }
}
