/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.retrieval;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shipped scorer is exact whole-word overlap over the entry's name plus description:
 * shared vocabulary ranks above none, and a query with no word in common with an entry
 * scores exactly zero rather than a small positive from fuzzy matching.
 */
class LexicalScorerTest {

	private final LexicalScorer scorer = new LexicalScorer();

	@Test
	void aQuerySharingTwoWordsOutranksOneSharingNone() {
		// Both query words come from the description, never the name, so a scorer that
		// matched the name alone would score the query zero and the comparison would
		// fail.
		SpecEntry entry = new SpecEntry("invoice-recon", "invoice-recon", "reconcile invoice totals", List.of(),
				"Report");

		double twoWords = this.scorer.score("reconcile totals", entry);
		double noneShared = this.scorer.score("weather forecast", entry);

		assertThat(noneShared).isZero();
		assertThat(twoWords).isGreaterThan(noneShared);
	}

	@Test
	void anUnrelatedQueryScoresZero() {
		SpecEntry entry = new SpecEntry("invoice-recon", "invoice-recon", "reconcile invoice totals", List.of(),
				"Report");

		assertThat(this.scorer.score("weather forecast tomorrow", entry)).isZero();
	}

	@Test
	void aBlankQueryScoresZeroRatherThanMatchingEverything() {
		SpecEntry entry = new SpecEntry("invoice-recon", "invoice-recon", "reconcile invoice totals", List.of(),
				"Report");

		assertThat(this.scorer.score("", entry)).isZero();
	}

}
