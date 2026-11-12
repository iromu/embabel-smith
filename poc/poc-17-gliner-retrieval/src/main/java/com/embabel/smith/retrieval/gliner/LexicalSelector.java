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
import java.util.Map;

import com.embabel.smith.retrieval.LexicalSimilarity;
import com.embabel.smith.retrieval.SpecEntry;

/**
 * The POC 8 lexical baseline exposed as a {@link SpecSelector}: argmax over word
 * term-frequency cosine similarity. Reused verbatim from POC 8 (via
 * {@link LexicalSimilarity}) so the POC 17 three-method comparison is apples-to-apples
 * with POC 12.
 *
 * @author Ivan Rodriguez
 */
public final class LexicalSelector implements SpecSelector {

	@Override
	public String select(String query, List<SpecEntry> candidates) {
		if (candidates.isEmpty()) {
			return null;
		}
		Map<String, Integer> queryTf = LexicalSimilarity.tf(LexicalSimilarity.tokenize(query));
		return candidates.stream()
			.max((a, b) -> Double.compare(score(queryTf, a), score(queryTf, b)))
			.map(SpecEntry::id)
			.orElse(null);
	}

	private static double score(Map<String, Integer> queryTf, SpecEntry entry) {
		String text = entry.name() + " " + entry.description();
		return LexicalSimilarity.cosine(queryTf, LexicalSimilarity.tf(LexicalSimilarity.tokenize(text)));
	}

}
