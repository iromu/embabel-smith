package com.embabel.smith.runtime.core;

import java.util.Set;

/**
 * The product trust tiers (the POC 10 tier model collapsed to the two tiers the CLI exposes).
 * Each tier allows a set of tool-group roles; a step may only run under a tier whose allowlist
 * covers the tool groups its pack declares.
 */
public enum TrustTier {

    /** The default tier: everything except high-trust tool groups such as {@code shell}. */
    STANDARD(Set.of("search", "web", "file")),

    /** The high-trust tier: all tool groups, including {@code shell}. */
    HIGH(Set.of("*"));

    private final Set<String> allowedToolGroups;

    TrustTier(Set<String> allowedToolGroups) {
        this.allowedToolGroups = allowedToolGroups;
    }

    /** Whether this tier permits the named tool-group role. */
    public boolean allows(String toolGroupRole) {
        return allowedToolGroups.contains("*") || allowedToolGroups.contains(toolGroupRole);
    }
}
