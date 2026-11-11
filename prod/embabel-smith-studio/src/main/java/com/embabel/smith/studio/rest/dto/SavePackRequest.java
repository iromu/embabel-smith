package com.embabel.smith.studio.rest.dto;

import com.embabel.smith.common.model.SkillPack;

/**
 * The save-a-pack request: the {@link SkillPack} to persist to the durable store.
 */
public record SavePackRequest(SkillPack pack) {
}
