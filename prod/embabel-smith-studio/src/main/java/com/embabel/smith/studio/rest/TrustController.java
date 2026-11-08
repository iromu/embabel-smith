package com.embabel.smith.studio.rest;

import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.runtime.core.TrustTier;
import com.embabel.smith.runtime.core.TrustTierPolicy;
import com.embabel.smith.studio.rest.dto.TrustRequest;
import com.embabel.smith.studio.rest.dto.TrustResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The Trust pane (change 06): check a skill-pack's declared tool groups against a trust-tier
 * allowlist with the change-03 runtime's policy. Offline — no model calls.
 */
@RestController
public class TrustController {

    private final SourceService sourceService;
    private final TrustTierPolicy policy;

    public TrustController(SourceService sourceService) {
        this.sourceService = sourceService;
        this.policy = new TrustTierPolicy();
    }

    @PostMapping("/api/trust")
    public TrustResponse check(@RequestBody TrustRequest request) {
        if (request.source() == null || request.source().isBlank()
                || request.skill() == null || request.skill().isBlank()) {
            return TrustResponse.error("No source or skill selected; pick one to check.");
        }
        TrustTier tier = parseTier(request.tier());
        try {
            SkillPack pack = sourceService.readPack(request.source(), request.skill());
            List<TrustTierPolicy.Violation> violations = policy.violations(List.of(pack), tier);
            boolean allowed = violations.isEmpty();
            List<String> disallowed = allowed
                    ? List.of()
                    : violations.get(0).disallowedGroups();
            List<String> declared = List.copyOf(policy.declaredToolGroups(pack));
            return TrustResponse.ok(request.skill(), tier.name(), allowed, disallowed, declared);
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.toString() : e.getMessage();
            return TrustResponse.error("Trust check failed: " + message);
        }
    }

    private static TrustTier parseTier(String tier) {
        if (tier == null || tier.isBlank()) {
            return TrustTier.STANDARD;
        }
        try {
            return TrustTier.valueOf(tier.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return TrustTier.STANDARD;
        }
    }
}
