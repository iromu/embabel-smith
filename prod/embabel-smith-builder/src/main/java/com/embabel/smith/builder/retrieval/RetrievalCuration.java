/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.retrieval;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import com.embabel.smith.builder.lifecycle.LifecycleState;

/**
 * The two library-curation rules the retrieval ranking is filtered by: a retired pack is
 * not surfaced at all, and a degraded one has its score halved so it sinks. This is the
 * part of the retrieval story that the measured evidence says actually works — the POC
 * found no text-similarity index cures near-duplicate shadowing, but curation does.
 *
 * <p>
 * The state is read from the pack's {@code lifecycle.yml} sidecar (the same one the
 * Lifecycle pane renders and the improvement loop transitions), so there is no second,
 * in-memory copy of lifecycle state to disagree with the loop.
 *
 * <p>
 * A pack with <b>no</b> sidecar is surfaced at full score: a freshly authored pack has no
 * recorded contract to fail. {@code INCUBATING} is deliberately not de-ranked — doing so
 * would hide every pack the loop has not yet validated.
 *
 * @author Ivan Rodriguez
 */
public final class RetrievalCuration {

	/** A degraded pack's score is halved, the factor the POC ran. */
	private static final double DEGRADED_FACTOR = 0.5;

	/**
	 * Whether a pack in this state is surfaced by retrieval. Only a retired pack is out
	 * of the library; a null (sidecar-less) state is surfaced.
	 * @param state the pack's recorded lifecycle state, or {@code null} for no sidecar.
	 * @return whether the pack is surfaced by retrieval.
	 */
	public boolean surfaced(LifecycleState state) {
		return state != LifecycleState.RETIRED;
	}

	/**
	 * The factor a pack's score is multiplied by. Degraded packs sink; everything else,
	 * including a null state, keeps its score.
	 * @param state the pack's recorded lifecycle state, or {@code null} for no sidecar.
	 * @return the de-rank factor for the state.
	 */
	public double deRankFactor(LifecycleState state) {
		return (state != LifecycleState.DEGRADED) ? 1.0 : DEGRADED_FACTOR;
	}

	/**
	 * The score a pack is reported with: the scorer's own score, de-ranked per
	 * {@link #deRankFactor} and rounded to the four decimals the response carries.
	 * @param score the score the scorer gave the pack.
	 * @param state the pack's recorded lifecycle state, or {@code null} for no sidecar.
	 * @return the curated score to report.
	 */
	public double curatedScore(double score, LifecycleState state) {
		return Math.round(score * deRankFactor(state) * 10000.0) / 10000.0;
	}

	/**
	 * Apply both rules to a ranked list: drop the retired packs, de-rank the degraded
	 * ones, re-order by the curated score (a halved score can fall below a pack that
	 * ranked under it), and take the top {@code k}. The state is handed in per entry
	 * rather than read here, so the builder stays out of the caller's storage.
	 * @param ranked the ranked entries, as {@link SpecRanker} produced them.
	 * @param stateOf the pack's recorded lifecycle state, or {@code null} for no sidecar.
	 * @param k the number of top hits to return.
	 * @return the curated top-k, in descending curated-score order.
	 */
	public List<SpecRanker.ScoredEntry> curate(List<SpecRanker.ScoredEntry> ranked,
			Function<SpecEntry, LifecycleState> stateOf, int k) {
		return ranked.stream()
			.filter((scored) -> surfaced(stateOf.apply(scored.entry())))
			.map((scored) -> new SpecRanker.ScoredEntry(scored.entry(),
					curatedScore(scored.score(), stateOf.apply(scored.entry()))))
			.sorted(Comparator.comparingDouble(SpecRanker.ScoredEntry::score).reversed())
			.limit(Math.max(0, k))
			.toList();
	}

}
