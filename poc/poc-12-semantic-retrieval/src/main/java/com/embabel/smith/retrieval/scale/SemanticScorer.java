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

import com.embabel.smith.retrieval.SpecEntry;

/**
 * The dense retriever: cosine over feature-hashing char/word n-gram embeddings. The
 * comparison arm in POC 12 — the offline stand-in for a real embedding backend.
 *
 * @author Ivan Rodriguez
 */
public final class SemanticScorer implements SpecScorer {

	@Override
	public double score(String query, SpecEntry entry) {
		String text = entry.name() + " " + entry.description();
		return SemanticSimilarity.cosine(SemanticSimilarity.embed(query), SemanticSimilarity.embed(text));
	}

}
