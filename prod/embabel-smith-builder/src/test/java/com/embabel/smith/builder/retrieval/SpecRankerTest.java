/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.retrieval;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ranker's own contract — ordering, the top-k cut, and the two degenerate inputs. The
 * scorer is a stub that reads the score straight off the entry's description, so a
 * failure here is a ranking failure and never a scoring one.
 */
class SpecRankerTest {

	/** A deterministic stub scorer: the entry's description <em>is</em> its score. */
	private final SpecRanker ranker = new SpecRanker((query, entry) -> Double.parseDouble(entry.description()));

	@Test
	void returnsTheTwoBestInDescendingOrder() {
		List<SpecRanker.ScoredEntry> ranked = this.ranker.rank("q", entries("0.1", "0.9", "0.5"), 2);

		assertThat(ranked).hasSize(2);
		assertThat(ranked.get(0).entry().id()).isEqualTo("b");
		assertThat(ranked.get(0).score()).isEqualTo(0.9);
		assertThat(ranked.get(1).entry().id()).isEqualTo("c");
		assertThat(ranked.get(1).score()).isEqualTo(0.5);
	}

	@Test
	void aKAboveTheEntryCountReturnsEveryEntryOnce() {
		List<SpecRanker.ScoredEntry> ranked = this.ranker.rank("q", entries("0.1", "0.9", "0.5"), 10);

		assertThat(ranked).hasSize(3);
		assertThat(ranked).map((scored) -> scored.entry().id()).containsExactly("b", "c", "a");
	}

	@Test
	void anEmptyEntryListRanksToNoResults() {
		assertThat(this.ranker.rank("q", List.of(), 5)).isEmpty();
	}

	@Test
	void aBlankQueryRanksToNoResultsRatherThanKZeroScoredPacks() {
		// Every entry scores zero against a blank query, so without this the caller would
		// be handed an arbitrary k packs presented as a ranking.
		assertThat(this.ranker.rank("", entries("0.1", "0.9"), 2)).isEmpty();
		assertThat(this.ranker.rank(null, entries("0.1", "0.9"), 2)).isEmpty();
	}

	private static List<SpecEntry> entries(String... scores) {
		List<SpecEntry> out = new ArrayList<>();
		for (int i = 0; i < scores.length; i++) {
			String id = String.valueOf((char) ('a' + i));
			out.add(new SpecEntry(id, id, scores[i], List.of(), "Report"));
		}
		return out;
	}

}
