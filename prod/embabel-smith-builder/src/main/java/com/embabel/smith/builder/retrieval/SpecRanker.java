/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.retrieval;

import java.util.Comparator;
import java.util.List;

/**
 * Ranks index entries by a {@link SpecScorer}'s relevance to a query and keeps the score
 * beside the entry, so the caller can render the number that produced the order. The
 * scorer is injected, which is what makes the ranking method replaceable without touching
 * anything above it.
 *
 * @author Ivan Rodriguez
 */
public final class SpecRanker {

	private final SpecScorer scorer;

	public SpecRanker(SpecScorer scorer) {
		this.scorer = scorer;
	}

	/**
	 * Score every entry against {@code query}, sort descending, take the top {@code k}. A
	 * blank query and an empty entry list are degenerate input, not faults: both rank to
	 * no results rather than throwing or returning {@code k} zero-scored packs at random.
	 * A {@code k} above the entry count returns all of them — nothing is padded or
	 * repeated.
	 * @param query the task query to rank against.
	 * @param entries the entries to rank.
	 * @param k the number of top hits to return.
	 * @return the top-k entries with the score each earned, in descending order.
	 */
	public List<ScoredEntry> rank(String query, List<SpecEntry> entries, int k) {
		if (query == null || query.isBlank()) {
			return List.of();
		}
		return entries.stream()
			.map((entry) -> new ScoredEntry(entry, this.scorer.score(query, entry)))
			.sorted(Comparator.comparingDouble(ScoredEntry::score).reversed())
			.limit(Math.max(0, k))
			.toList();
	}

	/**
	 * One ranked hit: the entry and the score the scorer gave it.
	 *
	 * @param entry the ranked index entry.
	 * @param score the scorer's relevance score for it.
	 * @author Ivan Rodriguez
	 */
	public record ScoredEntry(SpecEntry entry, double score) {
	}

}
