package com.embabel.smith.unified.dto;

import java.util.List;

/**
 * The result of a trust-tier check (POC 10): whether the skill is allowed under the tier, which of
 * its tool groups are not permitted, and the full set it declares. {@link #error} is non-null when
 * the request could not be checked (no skill selected, an unknown skill, or an unknown tier).
 */
public record TrustResponse(
        String error,
        String skillName,
        String tier,
        boolean allowed,
        List<String> disallowedGroups,
        List<String> declaredToolGroups) {

    public static TrustResponse error(String error) {
        return new TrustResponse(error, null, null, false, List.of(), List.of());
    }

    public static TrustResponse ok(String skillName, String tier, boolean allowed,
                                   List<String> disallowed, List<String> declared) {
        return new TrustResponse(null, skillName, tier, allowed, disallowed, declared);
    }
}
