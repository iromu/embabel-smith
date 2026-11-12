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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.embabel.smith.retrieval.SpecEntry;
import com.embabel.smith.retrieval.scale.SpecScorer;

/**
 * A {@link SpecScorer} backed by a real {@link Embedder}: cosine between the (memoised)
 * query embedding and each spec's (memoised) embedding. The cached vectors are the
 * "semantic index" doc 03 §2.1 describes. The only difference from POC 12's
 * {@code SemanticScorer} is that the vectors come from a trained transformer rather than
 * hashed character/word n-grams — everything else (the seam, the registry, the P@1 math)
 * is reused verbatim, so the comparison is apples-to-apples.
 *
 * <p>
 * Both query and entry vectors are memoised by key, so a benchmark that scores the same
 * query against a growing library embeds each distinct query and each distinct spec
 * exactly once.
 *
 * @author Ivan Rodriguez
 */
public final class RealEmbeddingScorer implements SpecScorer {

	private final Embedder embedder;

	private final Map<String, float[]> queryVectors = new ConcurrentHashMap<>();

	private final Map<String, float[]> entryVectors = new ConcurrentHashMap<>();

	public RealEmbeddingScorer(Embedder embedder) {
		this.embedder = embedder;
	}

	@Override
	public double score(String query, SpecEntry entry) {
		float[] q = this.queryVectors.computeIfAbsent(query, this.embedder::embed);
		float[] e = this.entryVectors.computeIfAbsent(entry.id(), (k) -> this.embedder.embed(text(entry)));
		return EmbeddingMath.cosine(q, e);
	}

	/**
	 * The same text the lexical / dense baselines score, so the comparison is
	 * apples-to-apples.
	 * @param entry the spec whose name and description are rendered.
	 * @return the spec's name and description, space-joined.
	 */
	private static String text(SpecEntry entry) {
		return entry.name() + " " + entry.description();
	}

}
