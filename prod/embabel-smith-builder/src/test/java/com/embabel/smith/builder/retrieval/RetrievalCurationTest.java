/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.retrieval;

import java.util.List;
import java.util.Map;

import com.embabel.smith.builder.lifecycle.LifecycleState;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two curation rules and the absence-of-a-sidecar case: retired is out, degraded
 * sinks, everything else — including a pack with no recorded contract at all — keeps its
 * own score.
 */
class RetrievalCurationTest {

	private final RetrievalCuration curation = new RetrievalCuration();

	@Test
	void aRetiredPackIsNotSurfaced() {
		assertThat(this.curation.surfaced(LifecycleState.RETIRED)).isFalse();
	}

	@Test
	void aDegradedPackIsDeRankedToHalfItsScore() {
		assertThat(this.curation.deRankFactor(LifecycleState.DEGRADED)).isEqualTo(0.5);
		assertThat(this.curation.curatedScore(0.8, LifecycleState.DEGRADED)).isEqualTo(0.4);
	}

	@Test
	void anActivePackKeepsItsScore() {
		assertThat(this.curation.surfaced(LifecycleState.ACTIVE)).isTrue();
		assertThat(this.curation.curatedScore(0.8, LifecycleState.ACTIVE)).isEqualTo(0.8);
	}

	@Test
	void anIncubatingPackIsNotDeRanked() {
		// De-ranking incubating would hide every pack the loop has not validated yet.
		assertThat(this.curation.surfaced(LifecycleState.INCUBATING)).isTrue();
		assertThat(this.curation.curatedScore(0.8, LifecycleState.INCUBATING)).isEqualTo(0.8);
	}

	@Test
	void aPackWithNoSidecarIsTreatedAsHealthy() {
		assertThat(this.curation.surfaced(null)).isTrue();
		assertThat(this.curation.curatedScore(0.8, null)).isEqualTo(0.8);
	}

	@Test
	void curatingDropsARetiredTopHitAndLetsADegradedOneSink() {
		List<SpecRanker.ScoredEntry> ranked = List.of(new SpecRanker.ScoredEntry(entry("degraded"), 0.9),
				new SpecRanker.ScoredEntry(entry("healthy"), 0.6), new SpecRanker.ScoredEntry(entry("retired"), 0.99));

		List<SpecRanker.ScoredEntry> curated = this.curation.curate(ranked, RetrievalCurationTest::stateOf, 3);

		// Retired is gone even though it scored highest; degraded halved to 0.45 and so
		// now sits under the healthy pack it used to outrank.
		assertThat(curated).extracting((scored) -> scored.entry().id()).containsExactly("healthy", "degraded");
		assertThat(curated.get(1).score()).isEqualTo(0.45);
	}

	@Test
	void curatingTakesOnlyTheTopK() {
		List<SpecRanker.ScoredEntry> ranked = List.of(new SpecRanker.ScoredEntry(entry("healthy"), 0.6),
				new SpecRanker.ScoredEntry(entry("degraded"), 0.9));

		assertThat(this.curation.curate(ranked, RetrievalCurationTest::stateOf, 1))
			.extracting((scored) -> scored.entry().id())
			.containsExactly("healthy");
	}

	private static SpecEntry entry(String id) {
		return new SpecEntry(id, id, "text", List.of(), "Report");
	}

	/**
	 * The state of an entry, looked up by id. An id this test has no state for is an
	 * error rather than a null: a null reads as "this pack has no sidecar", which is
	 * precisely how a mis-keyed lookup hides behind a green build.
	 * @param entry the entry whose state the curation asked for.
	 * @return the state recorded for that entry.
	 */
	private static LifecycleState stateOf(SpecEntry entry) {
		if (!STATES.containsKey(entry.id())) {
			throw new IllegalStateException(
					"no state recorded for test entry '" + entry.id() + "'; keys are " + STATES.keySet());
		}
		return STATES.get(entry.id());
	}

	private static final Map<String, LifecycleState> STATES = Map.of("degraded", LifecycleState.DEGRADED, "healthy",
			LifecycleState.ACTIVE, "retired", LifecycleState.RETIRED);

}
