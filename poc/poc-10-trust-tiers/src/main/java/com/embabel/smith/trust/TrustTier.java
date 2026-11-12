package com.embabel.smith.trust;

import java.util.Set;

/**
 * The trust tiers from doc 03 §2.1 (and the survey's §6). Each tier allows a set of tool-group
 * roles; a spec may only run under a tier whose allowlist covers every tool group the spec
 * declares. {@code T4} allows everything.
 */
public enum TrustTier {

    /** Instructions only — no tool access, full isolation. */
    T1_COMMUNITY(Set.of()),

    /** Instructions + read-only tools (search, web fetch). */
    T2_VERIFIED(Set.of("search", "web")),

    /** Instructions + write tools (file, shell) + MCP. */
    T3_PARTNER(Set.of("search", "web", "file", "shell")),

    /** Full capabilities, including host-level execution. */
    T4_PLATFORM(Set.of("*"));

    private final Set<String> allowedToolGroups;

    TrustTier(Set<String> allowedToolGroups) {
        this.allowedToolGroups = allowedToolGroups;
    }

    /** Whether this tier permits the named tool-group role. */
    public boolean allows(String toolGroupRole) {
        return allowedToolGroups.contains("*") || allowedToolGroups.contains(toolGroupRole);
    }
}
