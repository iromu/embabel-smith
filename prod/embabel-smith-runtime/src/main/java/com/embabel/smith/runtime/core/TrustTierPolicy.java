package com.embabel.smith.runtime.core;

import com.embabel.smith.common.model.AgentSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Enforces the per-tier tool-group allowlist (POC 10): a pack is runnable under a tier only if
 * every tool group its actions declare is permitted by that tier. Checked per run, before
 * execution, so a blocked run never executes a high-trust step.
 */
public class TrustTierPolicy {

    /** One pack whose declared tool groups are not all permitted by the granted tier. */
    public record Violation(String packName, List<String> disallowedGroups) {
        public Violation {
            disallowedGroups = List.copyOf(disallowedGroups);
        }
    }

    /** The distinct tool-group roles a pack's actions declare. */
    public Set<String> declaredToolGroups(AgentSpec pack) {
        Set<String> groups = new TreeSet<>();
        pack.steps().forEach(step -> groups.addAll(step.toolGroups()));
        return groups;
    }

    /** The packs that cannot run under the given tier; empty when every pack is allowed. */
    public List<Violation> violations(List<AgentSpec> packs, TrustTier tier) {
        List<Violation> violations = new ArrayList<>();
        for (AgentSpec pack : packs) {
            List<String> disallowed = declaredToolGroups(pack).stream()
                    .filter(group -> !tier.allows(group))
                    .sorted()
                    .toList();
            if (!disallowed.isEmpty()) {
                violations.add(new Violation(pack.name(), disallowed));
            }
        }
        return violations;
    }
}
