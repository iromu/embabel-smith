package com.embabel.smith.builder;

import com.embabel.smith.common.model.SkillPack;

import java.util.List;

/**
 * The output of the {@code compileFromDescription} step: a draft skill (a {@link SkillPack})
 * produced by the natural-language compiler, together with any issues the compiler surfaced for
 * review. A draft with issues is still usable but must be reviewed before it is trusted.
 */
public record CompiledDraft(String agentName, SkillPack pack, List<String> issues) {

    public CompiledDraft {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public boolean hasIssues() {
        return !issues.isEmpty();
    }
}
