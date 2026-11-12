package com.embabel.smith.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * A agent spec: the on-disk unit of capability that the builder emits and the no-code runtime
 * ingests.
 *
 * <p>It is a <em>directory</em>, not a single file:
 * <pre>
 *   &lt;pack&gt;/
 *     manifest.yml          AgentSpecManifest
 *     goal.yml              GoalStepSpec  (export: true — the callable goal)
 *     steps/*.yml           ActionStepSpec (one file per action)
 *     types/*.yml           DynamicTypeDef (intermediate pure-data types)
 * </pre>
 *
 * <p>This aggregate is the in-memory form of that directory; it is not itself serialized to a
 * single YAML file. See {@code com.embabel.smith.common.io.AgentSpecReader} /
 * {@code AgentSpecWriter} for the directory (de)serialization.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentSpec(
        AgentSpecManifest manifest,
        GoalStepSpec goal,
        List<ActionStepSpec> steps,
        List<DynamicTypeDef> types) {

    public AgentSpec {
        steps = steps == null ? List.of() : List.copyOf(steps);
        types = types == null ? List.of() : List.copyOf(types);
    }

    public String name() {
        return manifest == null ? null : manifest.name();
    }

    public String version() {
        return manifest == null ? null : manifest.version();
    }

    /** The output type of the exported goal — the type the runtime returns for a run. */
    public String goalOutputType() {
        return goal == null ? null : goal.outputTypeName();
    }

    public ActionStepSpec step(String name) {
        return steps.stream().filter(s -> name.equals(s.name())).findFirst().orElse(null);
    }

    public DynamicTypeDef type(String name) {
        return types.stream().filter(t -> name.equals(t.name())).findFirst().orElse(null);
    }
}
