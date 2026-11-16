/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
/**
 * A named, typed own-property of a {@link DynamicTypeDef}.
 *
 * <p>
 * Maps the {@code ownProperties} entries of an Embabel {@code DynamicType} YAML document.
 * The {@code type} is a plain scalar ({@code string}, {@code integer}, {@code number},
 * {@code boolean}, or a type name) — no compiled class is required.
 * @param name the name of the property
 * @param type the scalar type name of the property
 * @param description a human-readable description of the property
 * @author Ivan Rodriguez
 */

public record PropertyDef(@JsonProperty("name") String name, @JsonProperty("type") String type,
		@JsonProperty("description") String description) {
}
