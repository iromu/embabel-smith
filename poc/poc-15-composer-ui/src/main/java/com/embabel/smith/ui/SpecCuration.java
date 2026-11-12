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

package com.embabel.smith.ui;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.lifecycle.LifecycleManager;
import com.embabel.smith.lifecycle.LifecycleState;
import com.embabel.smith.lifecycle.SpecContentHash;

import org.springframework.stereotype.Component;

/**
 * Library curation for the retrieval pane (POC 15), reusing POC 18's pure lifecycle
 * machinery: a spec's contract is its eval pass-rate, and its lifecycle (incubating →
 * active → degraded → retired) is driven by POC 18's {@link LifecycleManager}. The
 * retrieval pane uses this to curate the library — retired specs are dropped and degraded
 * ones de-ranked — rather than just scoring it.
 *
 * <p>
 * Each catalog spec is seeded as a validated {@link LifecycleState#ACTIVE} baseline (its
 * live content hash, an assumed-passing score of 1.0). {@link #curate} records a
 * freshly-measured pass-rate and moves the spec through the lifecycle, <em>persisting the
 * new state</em> so later re-evals build on it (POC 18's state machine is stateful —
 * retirement is terminal, promotion is from incubation). The content-hash / drift /
 * state-machine half is pure and offline; only the measured score (from a live re-eval)
 * is endpoint-gated.
 *
 * @author Ivan Rodriguez
 */
@Component
public class SpecCuration {

	/** The de-rank factor applied to a degraded spec's retrieval score. */
	private static final double DEGRADED_FACTOR = 0.5;

	private final LifecycleManager lifecycle = LifecycleManager.defaults();

	private final Map<String, AgentSpec> packs = new LinkedHashMap<>();

	private final Map<String, Baseline> baselines = new LinkedHashMap<>();

	private final Map<String, LifecycleState> states = new LinkedHashMap<>();

	private final Map<String, Double> currentScores = new HashMap<>();

	public SpecCuration(SpecCatalog catalog) {
		catalog.all().forEach((name, node) -> {
			AgentSpec pack = node.pack();
			this.packs.put(name, pack);
			this.baselines.put(name, new Baseline(SpecContentHash.of(pack), 1.0));
			this.states.put(name, LifecycleState.ACTIVE);
		});
	}

	/**
	 * The spec's current lifecycle state, re-derived by POC 18's state machine from its
	 * recorded state, the live content hash (content drift if the pack changed since
	 * baseline), and the latest measured score (score drift if it fell off the baseline).
	 * @param name the spec name to look up.
	 * @return the spec's current lifecycle state.
	 */
	public LifecycleState currentState(String name) {
		LifecycleState state = this.states.get(name);
		if (state == null) {
			return LifecycleState.INCUBATING;
		}
		Baseline b = this.baselines.get(name);
		double currentScore = this.currentScores.getOrDefault(name, b.score());
		boolean contentDrift = !b.contentHash().equals(SpecContentHash.of(this.packs.get(name)));
		return this.lifecycle.transition(state, contentDrift, b.score(), currentScore);
	}

	/**
	 * The spec's state before a re-eval (its recorded state re-derived from the last
	 * measured score).
	 * @param name the spec name to look up.
	 * @return the spec's state before a re-eval.
	 */
	public LifecycleState stateBefore(String name) {
		return currentState(name);
	}

	/**
	 * Record a freshly-measured eval pass-rate and move the spec to its (possibly new)
	 * state.
	 * @param name the spec name to curate.
	 * @param measuredScore the freshly-measured eval pass-rate.
	 * @return the spec's new lifecycle state.
	 */
	public LifecycleState curate(String name, double measuredScore) {
		this.currentScores.put(name, measuredScore);
		LifecycleState newState = currentState(name);
		this.states.put(name, newState);
		return newState;
	}

	/**
	 * The spec's recorded baseline (content hash, score).
	 * @param name the spec name to look up.
	 * @return the recorded baseline, or null when unknown.
	 */
	public Baseline baseline(String name) {
		return this.baselines.get(name);
	}

	/**
	 * The spec's live content hash (POC 18's {@link SpecContentHash}).
	 * @param name the spec name to look up.
	 * @return the spec's live content hash.
	 */
	public String currentHash(String name) {
		return SpecContentHash.of(this.packs.get(name));
	}

	/**
	 * True when the spec's content no longer matches its recorded baseline hash.
	 * @param name the spec name to check.
	 * @return true when the spec's content has drifted from its baseline.
	 */
	public boolean contentDrift(String name) {
		Baseline b = this.baselines.get(name);
		return b != null && !b.contentHash().equals(currentHash(name));
	}

	/**
	 * A retired spec is out of the library — it is not surfaced by retrieval.
	 * @param state the lifecycle state to check.
	 * @return true when the spec is surfaced by retrieval.
	 */
	public boolean surfaced(LifecycleState state) {
		return state != LifecycleState.RETIRED;
	}

	/**
	 * Degraded specs are de-ranked (their retrieval score is halved); the rest keep their
	 * score.
	 * @param state the lifecycle state to apply de-ranking to.
	 * @return the factor to multiply the spec's retrieval score by.
	 */
	public double deRankFactor(LifecycleState state) {
		return (state == LifecycleState.DEGRADED) ? DEGRADED_FACTOR : 1.0;
	}

	/**
	 * A spec's recorded baseline: the content hash and eval pass-rate it was validated
	 * at.
	 * @param contentHash the live content hash at validation time.
	 * @param score the eval pass-rate the spec was validated at.
	 */
	public record Baseline(String contentHash, double score) {
	}

}
