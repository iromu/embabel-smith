package com.embabel.smith.studio.improve;

import com.embabel.smith.builder.lifecycle.LifecycleState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LifecycleTransitionsTest {

	@Test
	void incubatingBecomesActiveOnPublish() {
		assertThat(LifecycleTransitions.onPublished(LifecycleState.INCUBATING)).isEqualTo(LifecycleState.ACTIVE);
	}

	@Test
	void degradedRecoversToActiveOnPublish() {
		assertThat(LifecycleTransitions.onPublished(LifecycleState.DEGRADED)).isEqualTo(LifecycleState.ACTIVE);
	}

	@Test
	void activeStaysActiveOnPublish() {
		assertThat(LifecycleTransitions.onPublished(LifecycleState.ACTIVE)).isEqualTo(LifecycleState.ACTIVE);
	}

	@Test
	void activeDegradesOnRegression() {
		assertThat(LifecycleTransitions.onRegression(LifecycleState.ACTIVE)).isEqualTo(LifecycleState.DEGRADED);
	}

	@Test
	void degradedRetiresOnSustainedRegression() {
		assertThat(LifecycleTransitions.onRegression(LifecycleState.DEGRADED)).isEqualTo(LifecycleState.RETIRED);
	}

	@Test
	void retiredStaysRetired() {
		assertThat(LifecycleTransitions.onRegression(LifecycleState.RETIRED)).isEqualTo(LifecycleState.RETIRED);
	}

}
