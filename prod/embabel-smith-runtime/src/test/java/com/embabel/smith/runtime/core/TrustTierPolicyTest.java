package com.embabel.smith.runtime.core;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrustTierPolicyTest {

	private final TrustTierPolicy policy = new TrustTierPolicy();

	private static AgentSpec pack(String name, String... toolGroups) {
		ActionStepSpec step = new ActionStepSpec(ActionStepSpec.STEP_TYPE, name + "Step", "step",
				List.of("com.embabel.agent.domain.io.UserInput"), "com.embabel.agent.spec.model.BackgroundMessage",
				"prompt", null, null, null, null, null, null, List.of(toolGroups));
		return new AgentSpec(new AgentSpecManifest(name, "1.0"), null, List.of(step), List.of());
	}

	@Test
	void shellIsBlockedUnderStandardAndAllowedUnderHigh() {
		AgentSpec shell = pack("shell", "shell");

		List<TrustTierPolicy.Violation> blocked = policy.violations(List.of(shell), TrustTier.STANDARD);
		assertThat(blocked).hasSize(1);
		assertThat(blocked.get(0).packName()).isEqualTo("shell");
		assertThat(blocked.get(0).disallowedGroups()).containsExactly("shell");

		assertThat(policy.violations(List.of(shell), TrustTier.HIGH)).isEmpty();
	}

	@Test
	void aCleanPackIsAllowedUnderEveryTier() {
		AgentSpec clean = pack("clean");

		assertThat(policy.violations(List.of(clean), TrustTier.STANDARD)).isEmpty();
		assertThat(policy.violations(List.of(clean), TrustTier.HIGH)).isEmpty();
	}

	@Test
	void reportsEveryOffendingPack() {
		List<TrustTierPolicy.Violation> violations = policy
			.violations(List.of(pack("a", "shell"), pack("clean"), pack("b", "shell")), TrustTier.STANDARD);

		assertThat(violations).extracting(TrustTierPolicy.Violation::packName).containsExactly("a", "b");
	}

	@Test
	void declaredToolGroupsAreTheDistinctGroupsAcrossSteps() {
		AgentSpec pack = pack("p", "shell");
		assertThat(policy.declaredToolGroups(pack)).containsExactly("shell");
	}

}
