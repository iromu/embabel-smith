package com.embabel.smith.builder.core;

import com.embabel.smith.common.model.AgentSpec;

import java.util.List;

/**
 * The output of the {@code compileFromDescription} step: a draft spec (a {@link AgentSpec})
 * produced by the natural-language compiler, together with any issues the compiler surfaced for
 * review. A draft with issues is still usable but must be reviewed before it is trusted.
 */
public record CompiledDraft(String agentName, AgentSpec pack, List<String> issues) {

    public CompiledDraft {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public boolean hasIssues() {
        return !issues.isEmpty();
    }
}
