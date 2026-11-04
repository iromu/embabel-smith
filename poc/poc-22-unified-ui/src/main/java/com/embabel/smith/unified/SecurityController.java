package com.embabel.smith.unified;

import com.embabel.smith.composer.SkillNode;
import com.embabel.smith.security.Finding;
import com.embabel.smith.security.ScanReport;
import com.embabel.smith.security.SkillSecurityScanner;
import com.embabel.smith.unified.dto.SecurityFinding;
import com.embabel.smith.unified.dto.SecurityRequest;
import com.embabel.smith.unified.dto.SecurityResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The Security pane (POC 22): scan a skill-pack at install time via POC 21's
 * {@link SkillSecurityScanner} — pure static analysis over the skill's own prompts, fully offline.
 * It flags prompt-injection text, over-privileged tool groups, network references, and
 * secret-exfiltration patterns, and reports a {@code SAFE}/{@code REVIEW}/{@code BLOCK} verdict plus
 * the minimum POC 10 trust tier at which the skill is safe to load.
 */
@RestController
public class SecurityController {

    private final SkillCatalog catalog;
    private final SkillSecurityScanner scanner = new SkillSecurityScanner();

    public SecurityController(SkillCatalog catalog) {
        this.catalog = catalog;
    }

    /** Scan a skill-pack from the palette and report its findings, verdict, and recommended tier. */
    @PostMapping("/api/security")
    public SecurityResponse security(@RequestBody SecurityRequest request) {
        String skillName = request.skillName() == null ? "" : request.skillName();
        if (skillName.isBlank()) {
            return SecurityResponse.error("No skill selected; pick a skill to scan first.");
        }
        SkillNode node = catalog.get(skillName);
        if (node == null) {
            return SecurityResponse.error("Unknown skill: " + skillName + ". Choose one from the palette.");
        }
        ScanReport report = scanner.scan(node.pack());
        List<SecurityFinding> findings = report.findings().stream()
                .map(SecurityController::toFinding)
                .toList();
        return SecurityResponse.ok(skillName, report.verdict().name(), report.recommendedTier().name(),
                report.summary(), findings);
    }

    private static SecurityFinding toFinding(Finding f) {
        return new SecurityFinding(f.severity().name(), f.category(), f.message(), f.evidence());
    }
}
