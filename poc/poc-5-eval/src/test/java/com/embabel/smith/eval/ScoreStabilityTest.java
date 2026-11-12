package com.embabel.smith.eval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Offline checks for the eval-score stability measurement (docs/04 caveat #4).
 */
class ScoreStabilityTest {

	@Test
	void consistentScoresAreStable() {
		ScoreStability s = ScoreStability.of(List.of(1.0, 1.0, 1.0));
		assertThat(s.count()).isEqualTo(3);
		assertThat(s.mean()).isEqualTo(1.0);
		assertThat(s.range()).isEqualTo(0.0);
		assertThat(s.stdev()).isEqualTo(0.0);
		assertThat(s.stable()).isTrue();
	}

	@Test
	void aNoisyScoreIsNotStable() {
		ScoreStability s = ScoreStability.of(List.of(1.0, 0.0, 1.0));
		assertThat(s.mean()).isCloseTo(2.0 / 3.0, within(1e-9));
		assertThat(s.min()).isEqualTo(0.0);
		assertThat(s.max()).isEqualTo(1.0);
		assertThat(s.range()).isEqualTo(1.0);
		assertThat(s.stable()).isFalse();
	}

	@Test
	void aSmallSpreadIsStillStable() {
		ScoreStability s = ScoreStability.of(List.of(1.0, 0.8, 1.0, 0.9));
		assertThat(s.range()).isCloseTo(0.2, within(1e-9));
		assertThat(s.stable()).isTrue();
	}

	@Test
	void anEmptyListIsADegenerateStableReport() {
		ScoreStability s = ScoreStability.of(List.of());
		assertThat(s.count()).isEqualTo(0);
		assertThat(s.mean()).isEqualTo(0.0);
		assertThat(s.stable()).isTrue();
	}

}
