package com.embabel.smith.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The skill-pack manifest — declared in {@code manifest.yml}.
 *
 * <p>Requires a name and a version; may declare a default planner (e.g. {@code GOAP}) and a
 * default model. The runtime honours these as per-pack defaults that a run may override.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SkillPackManifest(
        @JsonProperty("name") String name,
        @JsonProperty("version") String version,
        @JsonProperty("defaultPlanner") String defaultPlanner,
        @JsonProperty("defaultModel") String defaultModel) {

    public SkillPackManifest(String name, String version) {
        this(name, version, null, null);
    }
}
