/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
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
