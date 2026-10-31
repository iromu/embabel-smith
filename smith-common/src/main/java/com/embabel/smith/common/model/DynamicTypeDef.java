package com.embabel.smith.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A pure-data intermediate type — the skill-pack's view of an Embabel {@code DynamicType}.
 *
 * <p>Declared in {@code types/<name>.yml}. It names a type with a description and a set of
 * named, typed own-properties, and requires <em>no</em> compiled JVM class to exist. This is
 * what makes a skill-pack portable and ingestible without a build step.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DynamicTypeDef(
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("ownProperties") List<PropertyDef> ownProperties,
        @JsonProperty("parents") List<String> parents,
        @JsonProperty("creationPermitted") Boolean creationPermitted) {

    public DynamicTypeDef {
        ownProperties = ownProperties == null ? List.of() : List.copyOf(ownProperties);
        parents = parents == null ? List.of() : List.copyOf(parents);
    }

    public boolean isCreationPermitted() {
        return creationPermitted == null || creationPermitted;
    }

    public boolean hasProperty(String propertyName) {
        return ownProperties.stream().anyMatch(p -> propertyName.equals(p.name()));
    }
}
