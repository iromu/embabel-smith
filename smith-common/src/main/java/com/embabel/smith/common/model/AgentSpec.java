/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.common.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
/**
 * A agent spec: the on-disk unit of capability that the builder emits and the no-code
 * runtime ingests.
 *
 * <p>
 * It is a <em>directory</em>, not a single file: <pre>
 *   &lt;pack&gt;/
 *     manifest.yml          AgentSpecManifest
 *     goal.yml              GoalStepSpec  (export: true — the callable goal)
 *     steps/*.yml           ActionStepSpec (one file per action)
 *     types/*.yml           DynamicTypeDef (intermediate pure-data types)
 *     skills/&lt;name&gt;/SKILL.md  optional — Agent Skills the pack carries, which a step
 *                              names from its {@code references} list
 * </pre> A {@code skills/} directory beside the pack rather than inside it is shared by
 * every agent in that folder; only the immediate parent is consulted, never a chain of
 * ancestors, and the pack's own {@code skills/} shadows a shared name.
 *
 * <p>
 * This aggregate is the in-memory form of that directory; it is not itself serialized to
 * a single YAML file. See {@code com.embabel.smith.common.io.AgentSpecReader} /
 * {@code AgentSpecWriter} for the directory (de)serialization.
 * @param manifest the manifest of the spec (name, version, defaults)
 * @param goal the exported goal of the spec
 * @param steps the action steps of the spec
 * @param types the intermediate pure-data types of the spec
 * @author Ivan Rodriguez
 */

public record AgentSpec(AgentSpecManifest manifest, GoalStepSpec goal, List<ActionStepSpec> steps,
		List<DynamicTypeDef> types) {

	public AgentSpec {
		steps = (steps != null) ? List.copyOf(steps) : List.of();
		types = (types != null) ? List.copyOf(types) : List.of();
	}

	public String name() {
		return (this.manifest != null) ? this.manifest.name() : null;
	}

	public String version() {
		return (this.manifest != null) ? this.manifest.version() : null;
	}

	/** The output type of the exported goal — the type the runtime returns for a run. */
	public String goalOutputType() {
		return (this.goal != null) ? this.goal.outputTypeName() : null;
	}

	public ActionStepSpec step(String name) {
		return this.steps.stream().filter((s) -> name.equals(s.name())).findFirst().orElse(null);
	}

	public DynamicTypeDef type(String name) {
		return this.types.stream().filter((t) -> name.equals(t.name())).findFirst().orElse(null);
	}
}
