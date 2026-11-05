package com.embabel.smith.runtime.core;

import java.util.ArrayList;
import java.util.List;

/**
 * The run was blocked because a pack declares tool groups the granted trust tier does not allow.
 * Carries the violations so the error can name both the offending packs and the tier requirement.
 */
public class TrustTierBlockedException extends RuntimeException {

    private final List<TrustTierPolicy.Violation> violations;
    private final TrustTier grantedTier;

    public TrustTierBlockedException(TrustTier grantedTier, List<TrustTierPolicy.Violation> violations) {
        super(message(grantedTier, violations));
        this.violations = List.copyOf(violations);
        this.grantedTier = grantedTier;
    }

    private static String message(TrustTier grantedTier, List<TrustTierPolicy.Violation> violations) {
        List<String> parts = new ArrayList<>();
        for (TrustTierPolicy.Violation violation : violations) {
            parts.add("pack '" + violation.packName() + "' declares tool group(s) " + violation.disallowedGroups()
                    + " that require a higher trust tier (granted: " + grantedTier.name().toLowerCase() + ")");
        }
        return "Run blocked by trust tier: " + String.join("; ", parts)
                + ". Re-run with the higher tier to allow these steps.";
    }

    public List<TrustTierPolicy.Violation> violations() {
        return violations;
    }

    public TrustTier grantedTier() {
        return grantedTier;
    }
}
