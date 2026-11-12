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

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.embabel.smith.lifecycle.LifecycleState;
import com.embabel.smith.retrieval.SpecEntry;
import com.embabel.smith.retrieval.scale.LexicalScorer;
import com.embabel.smith.retrieval.scale.ScaleSpecRegistry;
import com.embabel.smith.retrieval.scale.SpecScorer;
import com.embabel.smith.ui.dto.RetrieveRequest;
import com.embabel.smith.ui.dto.RetrieveResponse;
import com.embabel.smith.ui.dto.RetrieveResult;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The retrieval pane behind the canvas (POC 15): given a natural-language query, rank the
 * catalog's specs by relevance and return the top-k — the "which specs would this task
 * route to" view doc 03 §2.1 asks the builder UI to expose. It reuses the same
 * {@link SpecScorer} seam the whole retrieval POC family shares (POC 8 lexical, POC 12
 * dense, POC 17 GLiNER2, POC 20 real embedding), so the default offline
 * {@link LexicalScorer} is a drop-in for a denser scorer.
 *
 * <p>
 * On top of the raw relevance ranking, the pane applies POC 18's library curation via
 * {@link SpecCuration}: a retired spec is dropped from the results entirely, and a
 * degraded one is de-ranked (its score halved, so it falls in the ranking) — the library
 * is curated, not just scored.
 *
 * @author Ivan Rodriguez
 */
@RestController
public class RetrievalController {

	private final ScaleSpecRegistry registry;

	private final SpecScorer scorer;

	private final String scorerName;

	private final SpecCuration curation;

	/**
	 * Offline default: the POC 8 lexical scorer over the catalog.
	 * @param catalog the spec catalog to build the registry from.
	 * @param curation the library curation applied to ranked results.
	 */
	@Autowired
	public RetrievalController(SpecCatalog catalog, SpecCuration curation) {
		this(catalog, curation, new LexicalScorer(), "lexical");
	}

	/**
	 * Pluggable scorer — the seam POC 20's real {@code EmbeddingService} drops into.
	 * @param catalog the spec catalog to build the registry from.
	 * @param curation the library curation applied to ranked results.
	 * @param scorer the relevance scorer used for ranking.
	 * @param scorerName the scorer name reported in responses.
	 */
	public RetrievalController(SpecCatalog catalog, SpecCuration curation, SpecScorer scorer, String scorerName) {
		this.scorer = scorer;
		this.scorerName = scorerName;
		this.curation = curation;
		this.registry = new ScaleSpecRegistry(catalog.retrievalEntries(), scorer);
	}

	/**
	 * Rank the catalog by relevance to the query, curate it, and return the top-k with
	 * scores.
	 * @param request the retrieval request (query and k).
	 * @return the ranked, curated top-k results.
	 */
	@PostMapping("/api/retrieve")
	public RetrieveResponse retrieve(@RequestBody RetrieveRequest request) {
		String query = (request.query() == null) ? "" : request.query();
		int k = request.kOrDefault();
		List<RetrieveResult> results = this.registry.retrieve(query, this.registry.size())
			.stream()
			.map((e) -> curated(query, e))
			.filter(Objects::nonNull)
			.sorted(Comparator.comparingDouble(RetrieveResult::score).reversed())
			.limit(k)
			.toList();
		return new RetrieveResponse(this.scorerName, results);
	}

	/**
	 * Curate one ranked hit: drop it if its spec is retired, otherwise de-rank it if
	 * degraded and stamp its lifecycle state. Returns null for a retired (out-of-library)
	 * spec.
	 * @param query the natural-language query being ranked against.
	 * @param e the ranked catalog entry to curate.
	 * @return the curated result, or null when the spec is retired.
	 */
	private RetrieveResult curated(String query, SpecEntry e) {
		LifecycleState state = this.curation.currentState(e.name());
		if (!this.curation.surfaced(state)) {
			return null;
		}
		double adjusted = round4(this.scorer.score(query, e) * this.curation.deRankFactor(state));
		return new RetrieveResult(e.name(), e.description(), adjusted, e.outputType(), state.name());
	}

	private static double round4(double v) {
		return Math.round(v * 10000.0) / 10000.0;
	}

}
