package com.embabel.smith.unified;

import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.composer.SkillNode;
import com.embabel.smith.trust.TrustTier;
import com.embabel.smith.trust.TrustTierPolicy;
import com.embabel.smith.unified.dto.TrustRequest;
import com.embabel.smith.unified.dto.TrustResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * The Trust pane (POC 22): check a skill's declared tool groups against a POC 10 trust-tier allowlist
 * via {@link TrustTierPolicy} — the "tool-group allowlist per trust tier" that gates high-privilege
 * skills (e.g. {@code shell}) behind {@code T3}+ contexts. Pure and offline.
 */
@RestController
public class TrustController {

    private final SkillCatalog catalog;
    private final TrustTierPolicy policy = new TrustTierPolicy();

    public TrustController(SkillCatalog catalog) {
        this.catalog = catalog;
    }

    /** Check a skill from the palette against a tier; report whether it is allowed and which groups are not. */
    @PostMapping("/api/trust")
    public TrustResponse trust(@RequestBody TrustRequest request) {
        String skillName = request.skillName() == null ? "" : request.skillName();
        if (skillName.isBlank()) {
            return TrustResponse.error("No skill selected; pick a skill to check first.");
        }
        SkillNode node = catalog.get(skillName);
        if (node == null) {
            return TrustResponse.error("Unknown skill: " + skillName + ". Choose one from the palette.");
        }
        String tierName = request.tier() == null || request.tier().isBlank() ? "T3_PARTNER" : request.tier();
        TrustTier tier;
        try {
            tier = TrustTier.valueOf(tierName);
        } catch (IllegalArgumentException e) {
            return TrustResponse.error("Unknown tier: " + tierName + ". Use one of "
                    + Arrays.toString(TrustTier.values()));
        }
        SkillPack pack = node.pack();
        TrustTierPolicy.CheckResult result = policy.check(pack, tier);
        List<String> declared = policy.declaredToolGroups(pack).stream().toList();
        return TrustResponse.ok(skillName, tier.name(), result.allowed(), result.disallowedGroups(), declared);
    }
}
