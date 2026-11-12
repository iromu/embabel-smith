package com.embabel.smith.studio.rest;

import com.embabel.smith.builder.security.ScanReport;
import com.embabel.smith.builder.security.AgentSpecSecurityScanner;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.studio.rest.dto.ScanInfo;
import com.embabel.smith.studio.rest.dto.SecurityRequest;
import com.embabel.smith.studio.rest.dto.SecurityResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Security pane (change 06): scan a agent spec from a source at install time with the change-04
 * builder's static scanner. Offline — pure static analysis, no model calls.
 */
@RestController
public class SecurityController {

    private final SourceService sourceService;
    private final AgentSpecSecurityScanner scanner;

    public SecurityController(SourceService sourceService) {
        this.sourceService = sourceService;
        this.scanner = new AgentSpecSecurityScanner();
    }

    @PostMapping("/api/security")
    public SecurityResponse scan(@RequestBody SecurityRequest request) {
        if (request.source() == null || request.source().isBlank()
                || request.spec() == null || request.spec().isBlank()) {
            return SecurityResponse.error("No source or spec selected; pick one to scan.");
        }
        try {
            AgentSpec pack = sourceService.readPack(request.source(), request.spec());
            ScanReport report = scanner.scan(pack);
            return SecurityResponse.ok(ScanInfo.of(report));
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.toString() : e.getMessage();
            return SecurityResponse.error("Scan failed: " + message);
        }
    }
}
