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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
/**
 * A pure-data intermediate type — the agent spec's view of an Embabel
 * {@code DynamicType}.
 *
 * <p>
 * Declared in {@code types/<name>.yml}. It names a type with a description and a set of
 * named, typed own-properties, and requires <em>no</em> compiled JVM class to exist. This
 * is what makes a agent spec portable and ingestible without a build step.
 *
 * @param name the name of the type
 * @param description a human-readable description of the type
 * @param ownProperties the named, typed own-properties of the type
 * @param parents the names of the parent types
 * @param creationPermitted whether instances of the type may be created, or {@code null}
 * @author Ivan Rodriguez
 */

public record DynamicTypeDef(@JsonProperty("name") String name, @JsonProperty("description") String description,
		@JsonProperty("ownProperties") List<PropertyDef> ownProperties, @JsonProperty("parents") List<String> parents,
		@JsonProperty("creationPermitted") Boolean creationPermitted) {

	public DynamicTypeDef {
		ownProperties = (ownProperties != null) ? List.copyOf(ownProperties) : List.of();
		parents = (parents != null) ? List.copyOf(parents) : List.of();
	}

	public boolean isCreationPermitted() {
		return this.creationPermitted == null || this.creationPermitted;
	}

	public boolean hasProperty(String propertyName) {
		return this.ownProperties.stream().anyMatch((p) -> propertyName.equals(p.name()));
	}
}
