package com.embabel.smith.trust;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import com.embabel.smith.common.samples.SampleAgentSpecs;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline proof of the per-tier tool-group allowlist (POC 10): a high-privilege spec is
 * refused under a low-trust tier and allowed under a tier that permits its groups.
 */
class TrustTierPolicyTest {

	private final TrustTierPolicy policy = new TrustTierPolicy();

	private AgentSpec specWithToolGroups(List<String> groups) {
		ActionStepSpec step = new ActionStepSpec("action", "a step", "a step that does something",
				List.of(SampleAgentSpecs.USER_INPUT), SampleAgentSpecs.BACKGROUND_MESSAGE, "do: {{userInput.content}}",
				null, null, null, null, null, null, groups);
		return new AgentSpec(new AgentSpecManifest("test-spec", "1.0.0"),
				GoalStepSpec.exportedGoal("done", "done", SampleAgentSpecs.BACKGROUND_MESSAGE), List.of(step),
				List.of());
	}

	@Test
	void aShellSpecIsRefusedUnderCommunityTier() {
		TrustTierPolicy.CheckResult result = policy.check(specWithToolGroups(List.of("shell")), TrustTier.T1_COMMUNITY);
		assertThat(result.allowed()).isFalse();
		assertThat(result.disallowedGroups()).containsExactly("shell");
	}

	@Test
	void aShellSpecIsAllowedUnderPartnerTier() {
		assertThat(policy.check(specWithToolGroups(List.of("shell")), TrustTier.T3_PARTNER).allowed()).isTrue();
	}

	@Test
	void aNoToolSpecIsAllowedEvenUnderCommunityTier() {
		assertThat(policy.check(specWithToolGroups(List.of()), TrustTier.T1_COMMUNITY).allowed()).isTrue();
	}

	@Test
	void aSpecNeedingTwoGroupsIsRefusedIfEitherIsMissing() {
		// T2 allows search+web but not shell, so search+shell is refused.
		TrustTierPolicy.CheckResult result = policy.check(specWithToolGroups(List.of("search", "shell")),
				TrustTier.T2_VERIFIED);
		assertThat(result.allowed()).isFalse();
		assertThat(result.disallowedGroups()).containsExactly("shell");
	}

	@Test
	void platformTierAllowsEverything() {
		assertThat(policy.check(specWithToolGroups(List.of("shell", "file", "web", "anything")), TrustTier.T4_PLATFORM)
			.allowed()).isTrue();
	}

}
