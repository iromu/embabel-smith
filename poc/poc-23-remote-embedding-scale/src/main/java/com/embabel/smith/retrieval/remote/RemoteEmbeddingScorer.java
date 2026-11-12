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

package com.embabel.smith.retrieval.remote;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.embabel.smith.retrieval.SpecEntry;
import com.embabel.smith.retrieval.scale.SemanticSimilarity;
import com.embabel.smith.retrieval.scale.SpecScorer;

/**
 * A {@link SpecScorer} backed by the remote {@link RemoteEmbedder}: cosine between the
 * (memoised) query embedding and each spec's (memoised) embedding. The same seam as POC
 * 12's {@code SemanticScorer} and POC 20's {@code RealEmbeddingScorer}, but the vectors
 * come from a network {@code EmbeddingService} rather than hashed n-grams or a local ONNX
 * model — so the comparison is apples-to-apples.
 *
 * <p>
 * Both query and entry vectors are memoised by key, so a benchmark that scores the same
 * query against a growing library embeds each distinct query and each distinct spec
 * exactly once.
 *
 * @author Ivan Rodriguez
 */
public final class RemoteEmbeddingScorer implements SpecScorer {

	private final RemoteEmbedder embedder;

	private final Map<String, double[]> queryVectors = new ConcurrentHashMap<>();

	private final Map<String, double[]> entryVectors = new ConcurrentHashMap<>();

	public RemoteEmbeddingScorer(RemoteEmbedder embedder) {
		this.embedder = embedder;
	}

	@Override
	public double score(String query, SpecEntry entry) {
		double[] q = this.queryVectors.computeIfAbsent(query, this.embedder::embed);
		double[] e = this.entryVectors.computeIfAbsent(entry.id(), (k) -> this.embedder.embed(text(entry)));
		return SemanticSimilarity.cosine(q, e);
	}

	/**
	 * The same text the lexical / dense baselines score, so the comparison is
	 * apples-to-apples.
	 * @param entry the entry to score.
	 * @return the entry's name and description.
	 */
	private static String text(SpecEntry entry) {
		return entry.name() + " " + entry.description();
	}

}
