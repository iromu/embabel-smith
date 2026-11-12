package com.embabel.smith.trust;

import com.embabel.smith.common.model.AgentSpec;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Enforces the per-tier tool-group allowlist (doc 03 §2.1): a spec is runnable under a tier only if
 * every tool group its actions declare is permitted by that tier. This is the "tool-group
 * allowlist per trust tier" that gates high-privilege specs (e.g. {@code shell}) behind
 * {@code T3}+ contexts.
 */
public class TrustTierPolicy {

    /** The outcome of a tier check: whether the spec is allowed, and which of its groups are not. */
    public record CheckResult(boolean allowed, List<String> disallowedGroups) {
        public CheckResult {
            disallowedGroups = disallowedGroups == null ? List.of() : List.copyOf(disallowedGroups);
        }
    }

    /** The distinct tool-group roles a spec's actions declare. */
    public Set<String> declaredToolGroups(AgentSpec spec) {
        Set<String> groups = new TreeSet<>();
        spec.steps().forEach(step -> groups.addAll(step.toolGroups()));
        return groups;
    }

    /** Checks a spec against a tier. */
    public CheckResult check(AgentSpec spec, TrustTier tier) {
        List<String> disallowed = declaredToolGroups(spec).stream()
                .filter(group -> !tier.allows(group))
                .sorted()
                .toList();
        return new CheckResult(disallowed.isEmpty(), disallowed);
    }
}
