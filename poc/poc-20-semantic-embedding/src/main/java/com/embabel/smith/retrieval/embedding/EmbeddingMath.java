/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.embabel.smith.retrieval.embedding;

/**
 * Pure vector math for the real-embedding scorer: mask-aware mean-pooling of a
 * transformer's {@code last_hidden_state}, L2-normalisation, and cosine similarity. Kept
 * free of any ONNX or tokenizer dependency so it is unit-testable offline (the model
 * itself is the gated part).
 *
 * @author Ivan Rodriguez
 */
public final class EmbeddingMath {

	private EmbeddingMath() {
	}

	/**
	 * Mean-pool a {@code [seq][dim]} matrix of token hidden states over the non-padded
	 * positions (those with {@code attentionMask[i] != 0}), producing a single
	 * {@code [dim]} sentence vector. This is the standard sentence-transformers pooling
	 * of a BERT encoder.
	 * @param hidden the {@code [seq][dim]} matrix of token hidden states to pool.
	 * @param attentionMask the attention mask marking the non-padded positions.
	 * @return the pooled sentence vector.
	 */
	public static float[] meanPool(float[][] hidden, long[] attentionMask) {
		int seq = hidden.length;
		int dim = (seq > 0) ? hidden[0].length : 0;
		float[] out = new float[dim];
		int count = 0;
		for (int i = 0; i < seq; i++) {
			if (attentionMask == null || i >= attentionMask.length || attentionMask[i] == 0) {
				continue;
			}
			count++;
			for (int d = 0; d < dim; d++) {
				out[d] += hidden[i][d];
			}
		}
		if (count == 0) {
			return out;
		}
		for (int d = 0; d < dim; d++) {
			out[d] /= count;
		}
		return out;
	}

	/**
	 * L2-normalise a vector in place-safe fashion (returns a new vector); a zero vector
	 * is unchanged.
	 * @param v the vector to normalise.
	 * @return the L2-normalised vector (the input itself when its norm is zero).
	 */
	public static float[] normalize(float[] v) {
		double norm = 0;
		for (double x : v) {
			norm += x * x;
		}
		norm = Math.sqrt(norm);
		if (norm == 0) {
			return v;
		}
		float[] out = new float[v.length];
		for (int i = 0; i < v.length; i++) {
			out[i] = (float) (v[i] / norm);
		}
		return out;
	}

	/**
	 * Cosine similarity between two dense vectors; {@code 0.0} when either is
	 * null/zero/mismatched.
	 * @param a the first vector.
	 * @param b the second vector.
	 * @return the cosine similarity, or {@code 0.0} when either is null/zero/mismatched.
	 */
	public static double cosine(float[] a, float[] b) {
		if (a == null || b == null || a.length != b.length) {
			return 0.0;
		}
		double dot = 0;
		double na = 0;
		double nb = 0;
		for (int i = 0; i < a.length; i++) {
			dot += a[i] * b[i];
			na += a[i] * a[i];
			nb += b[i] * b[i];
		}
		if (na == 0 || nb == 0) {
			return 0.0;
		}
		return dot / (Math.sqrt(na) * Math.sqrt(nb));
	}

}
