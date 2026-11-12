package com.embabel.smith.unified;

import com.embabel.smith.composer.SpecNode;
import com.embabel.smith.security.Finding;
import com.embabel.smith.security.ScanReport;
import com.embabel.smith.security.SpecSecurityScanner;
import com.embabel.smith.unified.dto.SecurityFinding;
import com.embabel.smith.unified.dto.SecurityRequest;
import com.embabel.smith.unified.dto.SecurityResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The Security pane (POC 22): scan a agent spec at install time via POC 21's
 * {@link SpecSecurityScanner} — pure static analysis over the spec's own prompts, fully offline.
 * It flags prompt-injection text, over-privileged tool groups, network references, and
 * secret-exfiltration patterns, and reports a {@code SAFE}/{@code REVIEW}/{@code BLOCK} verdict plus
 * the minimum POC 10 trust tier at which the spec is safe to load.
 */
@RestController
public class SecurityController {

    private final SpecCatalog catalog;
    private final SpecSecurityScanner scanner = new SpecSecurityScanner();

    public SecurityController(SpecCatalog catalog) {
        this.catalog = catalog;
    }

    /** Scan a agent spec from the palette and report its findings, verdict, and recommended tier. */
    @PostMapping("/api/security")
    public SecurityResponse security(@RequestBody SecurityRequest request) {
        String specName = request.specName() == null ? "" : request.specName();
        if (specName.isBlank()) {
            return SecurityResponse.error("No spec selected; pick a spec to scan first.");
        }
        SpecNode node = catalog.get(specName);
        if (node == null) {
            return SecurityResponse.error("Unknown spec: " + specName + ". Choose one from the palette.");
        }
        ScanReport report = scanner.scan(node.pack());
        List<SecurityFinding> findings = report.findings().stream()
                .map(SecurityController::toFinding)
                .toList();
        return SecurityResponse.ok(specName, report.verdict().name(), report.recommendedTier().name(),
                report.summary(), findings);
    }

    private static SecurityFinding toFinding(Finding f) {
        return new SecurityFinding(f.severity().name(), f.category(), f.message(), f.evidence());
    }
}
