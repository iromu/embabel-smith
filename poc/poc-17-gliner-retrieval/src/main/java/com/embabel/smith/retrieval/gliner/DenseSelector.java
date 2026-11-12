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

package com.embabel.smith.retrieval.gliner;

import java.util.List;

import com.embabel.smith.retrieval.SpecEntry;
import com.embabel.smith.retrieval.scale.SemanticSimilarity;

/**
 * The POC 12 dense baseline exposed as a {@link SpecSelector}: argmax over
 * feature-hashing char/word n-gram cosine similarity. Reused verbatim from POC 12 (via
 * {@link SemanticSimilarity}) as the offline stand-in for a real embedding backend.
 *
 * @author Ivan Rodriguez
 */
public final class DenseSelector implements SpecSelector {

	@Override
	public String select(String query, List<SpecEntry> candidates) {
		if (candidates.isEmpty()) {
			return null;
		}
		double[] queryVec = SemanticSimilarity.embed(query);
		return candidates.stream()
			.max((a, b) -> Double.compare(score(queryVec, a), score(queryVec, b)))
			.map(SpecEntry::id)
			.orElse(null);
	}

	private static double score(double[] queryVec, SpecEntry entry) {
		String text = entry.name() + " " + entry.description();
		return SemanticSimilarity.cosine(queryVec, SemanticSimilarity.embed(text));
	}

}
