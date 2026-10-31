package com.embabel.smith.runtime;

import com.embabel.agent.api.common.PlannerType;
import com.embabel.smith.common.model.SkillPack;

/**
 * Resolves which planner a run should use.
 *
 * <p>Precedence: an explicit per-run choice (e.g. {@code builder.planner}) &gt; the pack's
 * {@code manifest.defaultPlanner} &gt; {@link PlannerType#GOAP}. This keeps the planner selectable
 * per run while defaulting to the deterministic GOAP planner.
 */
public class PlannerResolver {

    public PlannerType resolve(String runOverride, SkillPack pack) {
        String chosen = firstNonBlank(runOverride,
                pack == null || pack.manifest() == null ? null : pack.manifest().defaultPlanner());
        if (chosen == null) {
            return PlannerType.GOAP;
        }
        try {
            return PlannerType.valueOf(chosen.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown planner '" + chosen + "'. Supported: "
                            + String.join(", ", java.util.Arrays.stream(PlannerType.values())
                            .map(Enum::name).toList()), e);
        }
    }

    private static String firstNonBlank(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isBlank()) {
                return c;
            }
        }
        return null;
    }
}
