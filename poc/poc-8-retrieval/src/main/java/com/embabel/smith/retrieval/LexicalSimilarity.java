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

package com.embabel.smith.retrieval;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A deterministic, dependency-free lexical similarity (cosine over term-frequency
 * vectors).
 *
 * <p>
 * This stands in for the embedding-based semantic index that doc 03 §2.1 sketches for a
 * production SpecRegistry. A lexical scorer is enough to demonstrate the <em>shape</em>
 * of the retrieval problem — narrowing a large same-typed candidate set by relevance —
 * and keeps the POC fully offline and reproducible. Swapping this for an
 * {@code EmbeddingService} is a drop-in change to {@link SpecRegistry#retrieve}.
 *
 * @author Ivan Rodriguez
 */
public final class LexicalSimilarity {

	private LexicalSimilarity() {
	}

	/**
	 * Lowercase, split on non-alphanumerics, drop tokens shorter than two characters.
	 * @param text the text to tokenize.
	 * @return the list of tokens.
	 */
	public static List<String> tokenize(String text) {
		if (text == null || text.isBlank()) {
			return List.of();
		}
		List<String> tokens = new ArrayList<>();
		for (String part : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
			if (part.length() >= 2) {
				tokens.add(part);
			}
		}
		return tokens;
	}

	/**
	 * Term-frequency map for a token list.
	 * @param tokens the token list to count.
	 * @return the term-frequency map.
	 */
	public static Map<String, Integer> tf(List<String> tokens) {
		Map<String, Integer> tf = new HashMap<>();
		for (String token : tokens) {
			tf.merge(token, 1, Integer::sum);
		}
		return tf;
	}

	/**
	 * Cosine similarity between two term-frequency vectors; {@code 0.0} when either is
	 * empty.
	 * @param a the first term-frequency vector.
	 * @param b the second term-frequency vector.
	 * @return the cosine similarity between the two vectors.
	 */
	public static double cosine(Map<String, Integer> a, Map<String, Integer> b) {
		if (a.isEmpty() || b.isEmpty()) {
			return 0.0;
		}
		double dot = 0;
		for (Map.Entry<String, Integer> e : a.entrySet()) {
			Integer other = b.get(e.getKey());
			if (other != null) {
				dot += (long) e.getValue() * other;
			}
		}
		if (dot == 0) {
			return 0.0;
		}
		return dot / (norm(a) * norm(b));
	}

	private static double norm(Map<String, Integer> v) {
		double sum = 0;
		for (int count : v.values()) {
			sum += (long) count * count;
		}
		return Math.sqrt(sum);
	}

}
