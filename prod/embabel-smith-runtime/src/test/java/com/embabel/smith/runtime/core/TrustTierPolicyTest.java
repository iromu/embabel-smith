package com.embabel.smith.runtime.core;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.common.model.SkillPackManifest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrustTierPolicyTest {

    private final TrustTierPolicy policy = new TrustTierPolicy();

    private static SkillPack pack(String name, String... toolGroups) {
        ActionStepSpec step = new ActionStepSpec(ActionStepSpec.STEP_TYPE, name + "Step", "step",
                List.of("com.embabel.agent.domain.io.UserInput"),
                "com.embabel.agent.spec.model.BackgroundMessage",
                "prompt", null, null, null, null, null, null, List.of(toolGroups));
        return new SkillPack(new SkillPackManifest(name, "1.0"), null, List.of(step), List.of());
    }

    @Test
    void shellIsBlockedUnderStandardAndAllowedUnderHigh() {
        SkillPack shell = pack("shell", "shell");

        List<TrustTierPolicy.Violation> blocked = policy.violations(List.of(shell), TrustTier.STANDARD);
        assertThat(blocked).hasSize(1);
        assertThat(blocked.get(0).packName()).isEqualTo("shell");
        assertThat(blocked.get(0).disallowedGroups()).containsExactly("shell");

        assertThat(policy.violations(List.of(shell), TrustTier.HIGH)).isEmpty();
    }

    @Test
    void aCleanPackIsAllowedUnderEveryTier() {
        SkillPack clean = pack("clean");

        assertThat(policy.violations(List.of(clean), TrustTier.STANDARD)).isEmpty();
        assertThat(policy.violations(List.of(clean), TrustTier.HIGH)).isEmpty();
    }

    @Test
    void reportsEveryOffendingPack() {
        List<TrustTierPolicy.Violation> violations = policy.violations(
                List.of(pack("a", "shell"), pack("clean"), pack("b", "shell")), TrustTier.STANDARD);

        assertThat(violations).extracting(TrustTierPolicy.Violation::packName)
                .containsExactly("a", "b");
    }

    @Test
    void declaredToolGroupsAreTheDistinctGroupsAcrossSteps() {
        SkillPack pack = pack("p", "shell");
        assertThat(policy.declaredToolGroups(pack)).containsExactly("shell");
    }
}
