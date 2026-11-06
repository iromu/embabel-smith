package com.embabel.smith.builder.core;

import com.embabel.smith.common.model.SkillPack;

import java.util.List;

/**
 * The output of the {@code validateDraft} step: the draft plus whether its bindings and schemas
 * are consistent. When {@code valid} is false, the draft MUST NOT be written (design D5) — the
 * builder reports the issues instead.
 */
public record ValidatedDraft(SkillPack pack, boolean valid, List<String> issues) {

    public ValidatedDraft {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
