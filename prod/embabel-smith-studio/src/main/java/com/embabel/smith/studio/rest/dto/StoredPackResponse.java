package com.embabel.smith.studio.rest.dto;

import com.embabel.smith.common.model.SkillPack;

/**
 * The load-a-saved-pack response: the reloaded {@link SkillPack} (serialisable to the frontend's
 * ComposePack shape) or a structured error when no such pack is stored.
 */
public record StoredPackResponse(SkillPack pack, String error) {

    public static StoredPackResponse of(SkillPack pack) {
        return new StoredPackResponse(pack, null);
    }

    public static StoredPackResponse failed(String message) {
        return new StoredPackResponse(null, message);
    }
}
