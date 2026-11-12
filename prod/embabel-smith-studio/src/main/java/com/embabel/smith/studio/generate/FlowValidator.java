package com.embabel.smith.studio.generate;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The flow-soundness check for a generated (or authored) pack, mirroring the Compose editor's
 * client-side rule: the goal must be reachable (some step produces its output type) and every step
 * input must be fed (produced by some step, or an initial type). Returns the list of problems —
 * empty when the flow is sound.
 */
public final class FlowValidator {

    private static final List<String> INITIAL_TYPES = List.of("UserInput");

    private FlowValidator() {
    }

    public static List<String> issues(AgentSpec pack) {
        List<String> issues = new ArrayList<>();
        if (pack.goal() == null || !pack.goal().isExported()) {
            issues.add("The flow needs an exported goal (the type the run returns).");
        }
        if (pack.steps().isEmpty()) {
            issues.add("The flow needs at least one action step.");
        }
        String goalOutput = pack.goal() == null ? null : pack.goal().outputTypeName();
        boolean goalProduced = pack.steps().stream()
                .anyMatch(s -> goalOutput != null && goalOutput.equals(s.outputTypeName()));
        if (!goalProduced) {
            issues.add("No step produces the goal's output type '" + goalOutput + "'; the goal is unreachable.");
        }
        Set<String> produced = new HashSet<>();
        for (ActionStepSpec s : pack.steps()) {
            if (s.outputTypeName() != null) {
                produced.add(s.outputTypeName());
            }
        }
        for (ActionStepSpec s : pack.steps()) {
            for (String input : s.inputTypeNames()) {
                if (!produced.contains(input) && !INITIAL_TYPES.contains(input)) {
                    issues.add("Step '" + s.name() + "' needs input '" + input
                            + "', which no step produces.");
                }
            }
        }
        return issues;
    }
}
