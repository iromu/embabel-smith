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

package com.embabel.smith.unified;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.embabel.smith.lifecycle.LifecycleState;
import com.embabel.smith.retrieval.SpecEntry;
import com.embabel.smith.retrieval.scale.ScaleSpecRegistry;
import com.embabel.smith.retrieval.scale.SpecScorer;
import com.embabel.smith.unified.dto.RetrieveRequest;
import com.embabel.smith.unified.dto.RetrieveResponse;
import com.embabel.smith.unified.dto.RetrieveResult;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Retrieve pane (POC 22): given a natural-language query, rank the catalog's specs by
 * relevance and return the top-k — the "which specs would this task route to" view. It
 * reuses the same {@link SpecScorer} seam the whole retrieval POC family shares (POC 8
 * lexical, POC 12 dense, POC 17 GLiNER2, POC 20 real embedding); the active scorer is a
 * {@link ScorerBinding} bean, so a denser scorer drops in without changing the pane's
 * interface, and the response carries the active scorer name.
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
public class RetrieveController {

	private final ScaleSpecRegistry registry;

	private final SpecScorer scorer;

	private final String scorerName;

	private final SpecCuration curation;

	public RetrieveController(SpecCatalog catalog, SpecCuration curation, ScorerBinding binding) {
		this.scorer = binding.scorer();
		this.scorerName = binding.name();
		this.curation = curation;
		this.registry = new ScaleSpecRegistry(catalog.retrievalEntries(), this.scorer);
	}

	/**
	 * Rank the catalog by relevance to the query, curate it, and return the top-k with
	 * scores.
	 * @param request the retrieve request (query + k).
	 * @return the ranked, curated top-k results with the active scorer name.
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
	 * @param query the query the hit was ranked against.
	 * @param e the ranked spec entry to curate.
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
