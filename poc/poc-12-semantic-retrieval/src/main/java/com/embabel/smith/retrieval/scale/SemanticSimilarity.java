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

package com.embabel.smith.retrieval.scale;

import java.util.Locale;

/**
 * A lightweight, dependency-free <em>dense</em> similarity: feature-hashing
 * character/word n-gram embeddings + cosine.
 *
 * <p>
 * This is an offline, deterministic stand-in for the embedding backend that doc 03 §2.1
 * asks for ("build a semantic index ... using embeddings"). It is denser than POC 8's
 * word-TF lexical scorer because it also hashes sub-word character n-grams, so it
 * captures morphological and near-duplicate overlap that whole-word TF misses (e.g.
 * "coffee" and "coffeemaker" share character n-grams that "toaster" does not). It exposes
 * the same fixed-size-vector + cosine interface a real {@code EmbeddingService} would, so
 * the scale test is representative of the dense-retrieval regime without a model
 * endpoint.
 *
 * @author Ivan Rodriguez
 */
public final class SemanticSimilarity {

	/** Fixed embedding dimensionality (the hashed feature space). */
	public static final int DIMS = 256;

	private SemanticSimilarity() {
	}

	/**
	 * Embed {@code text} into a fixed-size, L2-normalised dense vector.
	 * @param text the text to embed.
	 * @return the L2-normalised dense vector for the text.
	 */
	public static double[] embed(String text) {
		String t = (text != null) ? text.toLowerCase(Locale.ROOT) : "";
		double[] v = new double[DIMS];
		// Whole words carry the primary signal.
		for (String w : t.split("[^a-z0-9]+")) {
			if (w.length() >= 2) {
				bump(v, w, 1.0);
			}
		}
		// Character 3- and 4-grams carry the sub-word / near-duplicate signal.
		for (int n = 3; n <= 4; n++) {
			double weight = (n != 3) ? 0.15 : 0.25;
			for (int i = 0; i + n <= t.length(); i++) {
				String gram = t.substring(i, i + n);
				if (gram.chars().allMatch(Character::isLetterOrDigit)) {
					bump(v, gram, weight);
				}
			}
		}
		return normalize(v);
	}

	/**
	 * Cosine similarity between two dense vectors; {@code 0.0} when either is
	 * null/zero/mismatched.
	 * @param a the first dense vector.
	 * @param b the second dense vector.
	 * @return the cosine similarity, or 0.0 when either vector is null/zero/mismatched.
	 */
	public static double cosine(double[] a, double[] b) {
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

	/**
	 * Signed feature hash: one dim from the feature's hash, sign from its high bit.
	 * @param v the vector to accumulate into.
	 * @param feature the hashed feature.
	 * @param weight the magnitude added to the chosen dim.
	 */
	private static void bump(double[] v, String feature, double weight) {
		int h = feature.hashCode();
		int idx = Math.floorMod(h, DIMS);
		int sign = ((h >>> 31) != 0) ? -1 : 1;
		v[idx] += sign * weight;
	}

	private static double[] normalize(double[] v) {
		double norm = 0;
		for (double x : v) {
			norm += x * x;
		}
		norm = Math.sqrt(norm);
		if (norm == 0) {
			return v;
		}
		for (int i = 0; i < v.length; i++) {
			v[i] /= norm;
		}
		return v;
	}

}
