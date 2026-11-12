package com.embabel.smith.runtime;

import com.embabel.agent.api.common.PlannerType;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.samples.SampleAgentSpecs;
import com.embabel.smith.common.model.AgentSpecManifest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies task 2.3 (offline part): the planner is selectable per run, defaults to GOAP, and
 * honours the manifest default when no per-run override is given.
 */
class PlannerResolverTest {

    private final PlannerResolver resolver = new PlannerResolver();

    @Test
    void defaultsToGoapWhenNothingIsSpecified() {
        AgentSpec pack = SampleAgentSpecs.explainer();
        // replace the manifest so it declares no default planner
        AgentSpec noDefault = new AgentSpec(new AgentSpecManifest("explainer", "1.0.0"),
                pack.goal(), pack.steps(), pack.types());

        assertThat(resolver.resolve(null, noDefault)).isEqualTo(PlannerType.GOAP);
        assertThat(resolver.resolve("  ", noDefault)).isEqualTo(PlannerType.GOAP);
    }

    @Test
    void anExplicitPerRunChoiceWins() {
        assertThat(resolver.resolve("UTILITY", SampleAgentSpecs.explainer())).isEqualTo(PlannerType.UTILITY);
        assertThat(resolver.resolve("hybrid", SampleAgentSpecs.explainer())).isEqualTo(PlannerType.HYBRID);
        assertThat(resolver.resolve("SUPERVISOR", SampleAgentSpecs.explainer())).isEqualTo(PlannerType.SUPERVISOR);
    }

    @Test
    void fallsBackToTheManifestDefault() {
        AgentSpec pack = SampleAgentSpecs.explainer(); // manifest declares defaultPlanner: GOAP
        assertThat(resolver.resolve(null, pack)).isEqualTo(PlannerType.GOAP);

        AgentSpec utilityDefault = new AgentSpec(new AgentSpecManifest("p", "1", "UTILITY", null),
                pack.goal(), pack.steps(), pack.types());
        assertThat(resolver.resolve(null, utilityDefault)).isEqualTo(PlannerType.UTILITY);
    }

    @Test
    void rejectsAnUnknownPlanner() {
        assertThatThrownBy(() -> resolver.resolve("NOT_A_PLANNER", SampleAgentSpecs.explainer()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NOT_A_PLANNER");
    }
}
