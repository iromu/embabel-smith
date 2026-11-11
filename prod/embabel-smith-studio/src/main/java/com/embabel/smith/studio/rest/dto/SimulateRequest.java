package com.embabel.smith.studio.rest.dto;

import com.embabel.smith.common.model.SkillPack;

/**
 * The simulate request: the generated (or edited) {@link SkillPack} to dry-run, and the sample
 * {@code input} bound to its {@code UserInput}. The pack is run in a throwaway platform; nothing is
 * published.
 */
public record SimulateRequest(SkillPack pack, String input) {
}
