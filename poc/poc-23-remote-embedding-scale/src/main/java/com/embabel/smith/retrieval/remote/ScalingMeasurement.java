package com.embabel.smith.retrieval.remote;

import com.embabel.smith.retrieval.SkillEntry;
import com.embabel.smith.retrieval.scale.ScaleRetrievalRunner;
import com.embabel.smith.retrieval.scale.ScaleSkillLibrary;
import com.embabel.smith.retrieval.scale.ScaleSkillRegistry;
import com.embabel.smith.retrieval.scale.SkillScorer;

import java.util.ArrayList;
import java.util.List;

/**
 * Measures the scaling dimension (POC 23): as a same-typed library grows, how the one-time
 * embedding cost and the steady-state per-query retrieval latency behave for an embedding index.
 *
 * <p>The embedding path embeds each skill <em>once</em> (memoised by the scorer) and then ranks
 * queries by pure-CPU cosine over the cached vectors — so per-query cost is O(n) arithmetic with
 * no model call, and the embedding work is amortised (n total, independent of query count). A
 * per-candidate LLM/transformer selector instead needs n model calls <em>per query</em>.
 */
public final class ScalingMeasurement {

    /** One point on the scaling curve. */
    public record Point(int librarySize, double precisionAt1, long indexBuildMillis, double perQueryMillis) {
    }

    private final ScaleRetrievalRunner runner = new ScaleRetrievalRunner();

    /**
     * Sweep ascending library sizes (prefixes of {@code library}). For each size: build the index
     * (embedding each new skill once via the shared memoising {@code scorer}), measure
     * Precision@1 on the benchmark, and time the steady-state per-query retrieval (all vectors
     * cached, so this isolates the O(n) ranking cost that grows with library size).
     */
    public List<Point> sweep(RemoteEmbedder embedder, List<SkillEntry> library,
                             List<ScaleSkillLibrary.QueryTarget> queries, List<Integer> sizes) {
        SkillScorer scorer = new RemoteEmbeddingScorer(embedder);
        List<Point> points = new ArrayList<>();
        for (int size : sizes) {
            List<SkillEntry> sub = library.subList(0, size);
            ScaleSkillRegistry registry = new ScaleSkillRegistry(sub, scorer);
            long t0 = System.nanoTime();
            double p1 = runner.precisionAt1(registry, queries);
            long indexBuildMillis = (System.nanoTime() - t0) / 1_000_000;
            double perQueryMillis = perQueryMillis(registry, queries);
            points.add(new Point(size, p1, indexBuildMillis, perQueryMillis));
        }
        return points;
    }

    /**
     * Mean steady-state per-query retrieval latency in ms: the vectors are already cached, so each
     * {@code retrieve} is pure-CPU cosine over the library. Warmed first, then timed over repeats.
     */
    private double perQueryMillis(ScaleSkillRegistry registry, List<ScaleSkillLibrary.QueryTarget> queries) {
        for (ScaleSkillLibrary.QueryTarget q : queries) {
            registry.retrieve(q.query(), 1);
        }
        int repeats = 3;
        long totalNanos = 0;
        for (int r = 0; r < repeats; r++) {
            long t0 = System.nanoTime();
            for (ScaleSkillLibrary.QueryTarget q : queries) {
                registry.retrieve(q.query(), 1);
            }
            totalNanos += System.nanoTime() - t0;
        }
        return (totalNanos / (double) repeats) / queries.size() / 1_000_000.0;
    }
}
