package com.embabel.smith.trust;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.common.model.SkillPackManifest;
import com.embabel.smith.common.samples.SampleSkillPacks;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline proof of the per-tier tool-group allowlist (POC 10): a high-privilege skill is refused
 * under a low-trust tier and allowed under a tier that permits its groups.
 */
class TrustTierPolicyTest {

    private final TrustTierPolicy policy = new TrustTierPolicy();

    private SkillPack skillWithToolGroups(List<String> groups) {
        ActionStepSpec step = new ActionStepSpec(
                "action", "a step", "a step that does something",
                List.of(SampleSkillPacks.USER_INPUT),
                SampleSkillPacks.BACKGROUND_MESSAGE, "do: {{userInput.content}}",
                null, null, null, null, null, null, groups);
        return new SkillPack(
                new SkillPackManifest("test-skill", "1.0.0"),
                GoalStepSpec.exportedGoal("done", "done", SampleSkillPacks.BACKGROUND_MESSAGE),
                List.of(step), List.of());
    }

    @Test
    void aShellSkillIsRefusedUnderCommunityTier() {
        TrustTierPolicy.CheckResult result = policy.check(skillWithToolGroups(List.of("shell")), TrustTier.T1_COMMUNITY);
        assertThat(result.allowed()).isFalse();
        assertThat(result.disallowedGroups()).containsExactly("shell");
    }

    @Test
    void aShellSkillIsAllowedUnderPartnerTier() {
        assertThat(policy.check(skillWithToolGroups(List.of("shell")), TrustTier.T3_PARTNER).allowed()).isTrue();
    }

    @Test
    void aNoToolSkillIsAllowedEvenUnderCommunityTier() {
        assertThat(policy.check(skillWithToolGroups(List.of()), TrustTier.T1_COMMUNITY).allowed()).isTrue();
    }

    @Test
    void aSkillNeedingTwoGroupsIsRefusedIfEitherIsMissing() {
        // T2 allows search+web but not shell, so search+shell is refused.
        TrustTierPolicy.CheckResult result =
                policy.check(skillWithToolGroups(List.of("search", "shell")), TrustTier.T2_VERIFIED);
        assertThat(result.allowed()).isFalse();
        assertThat(result.disallowedGroups()).containsExactly("shell");
    }

    @Test
    void platformTierAllowsEverything() {
        assertThat(policy.check(skillWithToolGroups(List.of("shell", "file", "web", "anything")),
                TrustTier.T4_PLATFORM).allowed()).isTrue();
    }
}
