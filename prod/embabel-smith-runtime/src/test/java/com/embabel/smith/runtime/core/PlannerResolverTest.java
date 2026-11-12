package com.embabel.smith.runtime.core;

import com.embabel.agent.api.common.PlannerType;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlannerResolverTest {

    private final PlannerResolver resolver = new PlannerResolver();

    private static AgentSpec pack(String defaultPlanner) {
        return new AgentSpec(new AgentSpecManifest("pack", "1.0.0", defaultPlanner, null), null, List.of(), List.of());
    }

    @Test
    void defaultsToGoapWhenNothingIsConfigured() {
        assertThat(resolver.resolve(null, null)).isEqualTo(PlannerType.GOAP);
        assertThat(resolver.resolve(null, pack(null))).isEqualTo(PlannerType.GOAP);
    }

    @Test
    void honoursThePackManifestDefault() {
        assertThat(resolver.resolve(null, pack("GOAP"))).isEqualTo(PlannerType.GOAP);
    }

    @Test
    void runOverrideWinsOverThePackDefault() {
        assertThat(resolver.resolve("GOAP", pack(null))).isEqualTo(PlannerType.GOAP);
    }

    @Test
    void rejectsAnUnknownPlannerNamingTheSupportedOnes() {
        assertThatThrownBy(() -> resolver.resolve("NOPE", pack(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown planner 'NOPE'")
                .hasMessageContaining("GOAP");
    }
}
