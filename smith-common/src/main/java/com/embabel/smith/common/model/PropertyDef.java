/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
 *
 * @param name the name of the property
 * @param type the scalar type name of the property
 * @param description a human-readable description of the property
 * @author Ivan Rodriguez
 */

public record PropertyDef(@JsonProperty("name") String name, @JsonProperty("type") String type,
		@JsonProperty("description") String description) {
}
