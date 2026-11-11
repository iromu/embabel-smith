package com.embabel.smith.studio.rest;

import com.embabel.smith.studio.discover.BusinessProcessDiscovery;
import com.embabel.smith.studio.rest.dto.DiscoverRequest;
import com.embabel.smith.studio.rest.dto.DiscoverResponse;
import com.embabel.smith.studio.store.ProcessStore;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The workspace's discovery/ingest step: load the business-process documents from a local docs
 * path or a repository URL, and return them for presentation. Offline for a local path (no model
 * call); a repository URL is cloned first. Each successful load is recorded to the durable
 * discovery history; the model-based extraction of a raw codebase is a separate step
 * ({@code /api/extract}).
 */
@RestController
public class DiscoverController {

    private final BusinessProcessDiscovery discovery;
    private final ProcessStore store;

    public DiscoverController(BusinessProcessDiscovery discovery, ProcessStore store) {
        this.discovery = discovery;
        this.store = store;
    }

    @PostMapping("/api/discover")
    public DiscoverResponse discover(@RequestBody DiscoverRequest request) {
        try {
            BusinessProcessDiscovery.DiscoverResult result = discovery.load(request.path(), request.url());
            recordDiscoverySafely(result);
            return DiscoverResponse.of(result.sourceType(), result.location(), result.docs());
        } catch (RuntimeException e) {
            return DiscoverResponse.failed(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    /** Discovery history is best-effort: a recording failure must not fail the load. */
    private void recordDiscoverySafely(BusinessProcessDiscovery.DiscoverResult result) {
        try {
            store.recordDiscovery(result.sourceType(), result.location(), result.docs().size());
        } catch (RuntimeException ignored) {
            // ignore — the docs still load
        }
    }
}
