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

import java.util.Comparator;
import java.util.List;

import com.embabel.smith.retrieval.SpecEntry;

/**
 * A scorer-agnostic spec registry: ranks {@link SpecEntry} by a pluggable
 * {@link SpecScorer} and returns the top-k. The same registry drives both the lexical
 * baseline and the dense semantic retriever, so the scale comparison in POC 12 is
 * apples-to-apples.
 *
 * @author Ivan Rodriguez
 */
public class ScaleSpecRegistry {

	private final List<SpecEntry> entries;

	private final SpecScorer scorer;

	public ScaleSpecRegistry(List<SpecEntry> entries, SpecScorer scorer) {
		this.entries = List.copyOf(entries);
		this.scorer = scorer;
	}

	public int size() {
		return this.entries.size();
	}

	/**
	 * Rank specs by the scorer's relevance to {@code query} and return the top {@code k}.
	 * @param query the task query to rank against.
	 * @param k the number of top hits to return.
	 * @return the top-k specs ranked by relevance.
	 */
	public List<SpecEntry> retrieve(String query, int k) {
		return this.entries.stream()
			.map((e) -> new Scored(e, this.scorer.score(query, e)))
			.sorted(Comparator.comparingDouble((Scored s) -> s.score).reversed())
			.limit(Math.max(0, k))
			.map(Scored::entry)
			.toList();
	}

	private record Scored(SpecEntry entry, double score) {
	}

}
