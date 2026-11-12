package com.embabel.smith.studio.rest.dto;

import java.util.List;

/**
 * The result of a trust-tier check (the Trust pane): whether the spec's declared tool groups are
 * all permitted by the tier, the disallowed groups, and the groups the spec declares.
 */
public record TrustResponse(String error, String specName, String tier, boolean allowed,
                            List<String> disallowedGroups, List<String> declaredToolGroups) {

    public static TrustResponse ok(String specName, String tier, boolean allowed,
                                   List<String> disallowedGroups, List<String> declaredToolGroups) {
        return new TrustResponse(null, specName, tier, allowed, disallowedGroups, declaredToolGroups);
    }

    public static TrustResponse error(String error) {
        return new TrustResponse(error, null, null, false, List.of(), List.of());
    }
}
