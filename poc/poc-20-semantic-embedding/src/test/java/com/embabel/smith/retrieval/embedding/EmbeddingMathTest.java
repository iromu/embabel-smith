package com.embabel.smith.retrieval.embedding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins the pure vector math (mean-pool, normalise, cosine) the real-embedding scorer
 * relies on.
 */
class EmbeddingMathTest {

	@Test
	void meanPoolAveragesOnlyNonPaddedPositions() {
		float[][] hidden = { { 1, 2 }, { 3, 4 }, { 100, 100 } };
		long[] mask = { 1, 1, 0 };
		assertThat(EmbeddingMath.meanPool(hidden, mask)).containsExactly(2.0f, 3.0f);
	}

	@Test
	void meanPoolWithAllPaddedPositionsIsZero() {
		float[][] hidden = { { 1, 2 }, { 3, 4 } };
		assertThat(EmbeddingMath.meanPool(hidden, new long[] { 0, 0 })).containsExactly(0.0f, 0.0f);
	}

	@Test
	void normalizeProducesAUnitVector() {
		float[] v = EmbeddingMath.normalize(new float[] { 3, 4 });
		assertThat(v).containsExactly(0.6f, 0.8f);
		assertThat(norm(v)).isEqualTo(1.0, within(1e-6));
	}

	@Test
	void normalizeLeavesAZeroVectorUnchanged() {
		assertThat(EmbeddingMath.normalize(new float[] { 0, 0 })).containsExactly(0.0f, 0.0f);
	}

	@Test
	void cosineCoversAlignedOrthogonalAndOpposed() {
		assertThat(EmbeddingMath.cosine(new float[] { 1, 0 }, new float[] { 1, 0 })).isEqualTo(1.0, within(1e-6));
		assertThat(EmbeddingMath.cosine(new float[] { 1, 0 }, new float[] { 0, 1 })).isEqualTo(0.0, within(1e-6));
		assertThat(EmbeddingMath.cosine(new float[] { 1, 0 }, new float[] { -1, 0 })).isEqualTo(-1.0, within(1e-6));
	}

	@Test
	void cosineIsZeroOnNullOrMismatchedVectors() {
		assertThat(EmbeddingMath.cosine(null, new float[] { 1, 0 })).isEqualTo(0.0);
		assertThat(EmbeddingMath.cosine(new float[] { 1 }, new float[] { 1, 0 })).isEqualTo(0.0);
		assertThat(EmbeddingMath.cosine(new float[] { 0, 0 }, new float[] { 1, 0 })).isEqualTo(0.0);
	}

	private static double norm(float[] v) {
		double s = 0;
		for (double x : v) {
			s += x * x;
		}
		return Math.sqrt(s);
	}

}
