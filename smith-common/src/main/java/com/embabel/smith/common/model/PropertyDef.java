package com.embabel.smith.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A named, typed own-property of a {@link DynamicTypeDef}.
 *
 * <p>Maps the {@code ownProperties} entries of an Embabel {@code DynamicType} YAML
 * document. The {@code type} is a plain scalar ({@code string}, {@code integer},
 * {@code number}, {@code boolean}, or a type name) — no compiled class is required.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PropertyDef(
        @JsonProperty("name") String name,
        @JsonProperty("type") String type,
        @JsonProperty("description") String description) {
}
