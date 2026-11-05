package com.embabel.smith.runtime.core;

import com.embabel.agent.api.common.PlannerType;
import com.embabel.smith.common.model.SkillPack;

import java.util.Arrays;

/**
 * Resolves which planner a run should use.
 *
 * <p>Precedence: an explicit per-run choice &gt; the goal pack's {@code manifest.defaultPlanner}
 * &gt; {@link PlannerType#GOAP}. This keeps the planner selectable per run while defaulting to the
 * deterministic GOAP planner.
 */
public class PlannerResolver {

    public PlannerType resolve(String runOverride, SkillPack goalPack) {
        String chosen = firstNonBlank(runOverride,
                goalPack == null || goalPack.manifest() == null ? null : goalPack.manifest().defaultPlanner());
        if (chosen == null) {
            return PlannerType.GOAP;
        }
        try {
            return PlannerType.valueOf(chosen.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown planner '" + chosen + "'. Supported: "
                            + String.join(", ", Arrays.stream(PlannerType.values()).map(Enum::name).toList()), e);
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
