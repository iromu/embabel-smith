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

import java.util.ArrayList;
import java.util.List;

import com.embabel.smith.retrieval.SpecEntry;

/**
 * The at-scale retrieval measurement (POC 12). Pure and offline: it quantifies selection
 * accuracy of a {@link SpecScorer} as a same-typed library grows and as near-duplicate
 * (shadowing) specs accumulate, and it compares the lexical baseline against the dense
 * semantic retriever.
 *
 * @author Ivan Rodriguez
 */
public class ScaleRetrievalRunner {

	/**
	 * Fraction of benchmark queries whose canonical target is the top hit.
	 * @param registry the spec registry to retrieve from.
	 * @param queries the benchmark queries.
	 * @return the fraction of queries whose canonical target is the top hit.
	 */
	public double precisionAt1(ScaleSpecRegistry registry, List<ScaleSpecLibrary.QueryTarget> queries) {
		return precisionInTopK(registry, queries, 1);
	}

	/**
	 * Fraction of benchmark queries whose canonical target appears in the top-k hits.
	 * @param registry the spec registry to retrieve from.
	 * @param queries the benchmark queries.
	 * @param k the number of hits to consider.
	 * @return the fraction of queries whose canonical target appears in the top-k hits.
	 */
	public double precisionInTopK(ScaleSpecRegistry registry, List<ScaleSpecLibrary.QueryTarget> queries, int k) {
		if (queries.isEmpty()) {
			return 0.0;
		}
		int hits = 0;
		for (ScaleSpecLibrary.QueryTarget q : queries) {
			List<SpecEntry> top = registry.retrieve(q.query(), k);
			if (top.stream().anyMatch((e) -> e.id().equals(q.targetId()))) {
				hits++;
			}
		}
		return (double) hits / queries.size();
	}

	/**
	 * Precision@1 across the benchmark as {@code sequence} specs are added one at a time
	 * to {@code core}. One point per added spec (plus the starting point), so the caller
	 * can sample the curve.
	 * @param core the starting library of distinct-topic specs.
	 * @param sequence the specs added one at a time.
	 * @param scorer the scorer used to rank the library.
	 * @param queries the benchmark queries.
	 * @return one precision point per added spec, starting from {@code core}.
	 */
	public List<CurvePoint> growthCurve(List<SpecEntry> core, List<SpecEntry> sequence, SpecScorer scorer,
			List<ScaleSpecLibrary.QueryTarget> queries) {
		List<SpecEntry> library = new ArrayList<>(core);
		List<CurvePoint> points = new ArrayList<>();
		points.add(new CurvePoint(library.size(), precisionAt1(new ScaleSpecRegistry(library, scorer), queries)));
		for (SpecEntry s : sequence) {
			library.add(s);
			points.add(new CurvePoint(library.size(), precisionAt1(new ScaleSpecRegistry(library, scorer), queries)));
		}
		return points;
	}

	/**
	 * For one benchmark query, the id of the top hit under {@code scorer} (or null if
	 * empty).
	 * @param registry the spec registry to retrieve from.
	 * @param query the benchmark query.
	 * @return the id of the top hit, or null if the registry is empty.
	 */
	public String topHitId(ScaleSpecRegistry registry, String query) {
		List<SpecEntry> top = registry.retrieve(query, 1);
		return top.isEmpty() ? null : top.get(0).id();
	}

	/**
	 * A point on a precision curve: the library size and precision@1 across the
	 * benchmark.
	 * @param librarySize the library size at this point.
	 * @param precisionAt1 the precision@1 across the benchmark at this point.
	 */
	public record CurvePoint(int librarySize, double precisionAt1) {
	}

}
