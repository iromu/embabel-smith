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

package com.embabel.smith.retrieval.remote;

import java.util.ArrayList;
import java.util.List;

import com.embabel.smith.retrieval.SpecEntry;
import com.embabel.smith.retrieval.scale.ScaleRetrievalRunner;
import com.embabel.smith.retrieval.scale.ScaleSpecLibrary;
import com.embabel.smith.retrieval.scale.ScaleSpecRegistry;
import com.embabel.smith.retrieval.scale.SpecScorer;

/**
 * Measures the scaling dimension (POC 23): as a same-typed library grows, how the
 * one-time embedding cost and the steady-state per-query retrieval latency behave for an
 * embedding index.
 *
 * <p>
 * The embedding path embeds each spec <em>once</em> (memoised by the scorer) and then
 * ranks queries by pure-CPU cosine over the cached vectors — so per-query cost is O(n)
 * arithmetic with no model call, and the embedding work is amortised (n total,
 * independent of query count). A per-candidate LLM/transformer selector instead needs n
 * model calls <em>per query</em>.
 *
 * @author Ivan Rodriguez
 */
public final class ScalingMeasurement {

	private final ScaleRetrievalRunner runner = new ScaleRetrievalRunner();

	/**
	 * Sweep ascending library sizes (prefixes of {@code library}). For each size: build
	 * the index (embedding each new spec once via the shared memoising {@code scorer}),
	 * measure Precision@1 on the benchmark, and time the steady-state per-query retrieval
	 * (all vectors cached, so this isolates the O(n) ranking cost that grows with library
	 * size).
	 * @param embedder the remote embedder.
	 * @param library the full library (prefixes are used).
	 * @param queries the benchmark queries.
	 * @param sizes the ascending library sizes to sweep.
	 * @return one point per size, in sweep order.
	 */
	public List<Point> sweep(RemoteEmbedder embedder, List<SpecEntry> library,
			List<ScaleSpecLibrary.QueryTarget> queries, List<Integer> sizes) {
		SpecScorer scorer = new RemoteEmbeddingScorer(embedder);
		List<Point> points = new ArrayList<>();
		for (int size : sizes) {
			List<SpecEntry> sub = library.subList(0, size);
			ScaleSpecRegistry registry = new ScaleSpecRegistry(sub, scorer);
			long t0 = System.nanoTime();
			double p1 = this.runner.precisionAt1(registry, queries);
			long indexBuildMillis = (System.nanoTime() - t0) / 1_000_000;
			double perQueryMillis = perQueryMillis(registry, queries);
			points.add(new Point(size, p1, indexBuildMillis, perQueryMillis));
		}
		return points;
	}

	/**
	 * Mean steady-state per-query retrieval latency in ms: the vectors are already
	 * cached, so each {@code retrieve} is pure-CPU cosine over the library. Warmed first,
	 * then timed over repeats.
	 * @param registry the registry to query.
	 * @param queries the queries to run.
	 * @return mean per-query retrieval latency in ms.
	 */
	private double perQueryMillis(ScaleSpecRegistry registry, List<ScaleSpecLibrary.QueryTarget> queries) {
		for (ScaleSpecLibrary.QueryTarget q : queries) {
			registry.retrieve(q.query(), 1);
		}
		int repeats = 3;
		long totalNanos = 0;
		for (int r = 0; r < repeats; r++) {
			long t0 = System.nanoTime();
			for (ScaleSpecLibrary.QueryTarget q : queries) {
				registry.retrieve(q.query(), 1);
			}
			totalNanos += System.nanoTime() - t0;
		}
		return (totalNanos / (double) repeats) / queries.size() / 1_000_000.0;
	}

	/**
	 * One point on the scaling curve.
	 * @param librarySize the library size for this point.
	 * @param precisionAt1 precision@1 on the benchmark.
	 * @param indexBuildMillis one-time index build cost in ms.
	 * @param perQueryMillis steady-state mean per-query latency in ms.
	 */
	public record Point(int librarySize, double precisionAt1, long indexBuildMillis, double perQueryMillis) {
	}

}
