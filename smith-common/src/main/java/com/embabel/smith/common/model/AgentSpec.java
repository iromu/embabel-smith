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
 * </pre>
 *
 * <p>
 * This aggregate is the in-memory form of that directory; it is not itself serialized to
 * a single YAML file. See {@code com.embabel.smith.common.io.AgentSpecReader} /
 * {@code AgentSpecWriter} for the directory (de)serialization.
 *
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
