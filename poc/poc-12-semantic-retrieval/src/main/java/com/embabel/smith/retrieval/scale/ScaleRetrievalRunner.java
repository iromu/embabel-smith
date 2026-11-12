package com.embabel.smith.retrieval.scale;

import com.embabel.smith.retrieval.SpecEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * The at-scale retrieval measurement (POC 12). Pure and offline: it quantifies selection accuracy
 * of a {@link SpecScorer} as a same-typed library grows and as near-duplicate (shadowing) specs
 * accumulate, and it compares the lexical baseline against the dense semantic retriever.
 */
public class ScaleRetrievalRunner {

    /** A point on a precision curve: the library size and precision@1 across the benchmark. */
    public record CurvePoint(int librarySize, double precisionAt1) {
    }

    /** Fraction of benchmark queries whose canonical target is the top hit. */
    public double precisionAt1(ScaleSpecRegistry registry, List<ScaleSpecLibrary.QueryTarget> queries) {
        return precisionInTopK(registry, queries, 1);
    }

    /** Fraction of benchmark queries whose canonical target appears in the top-k hits. */
    public double precisionInTopK(ScaleSpecRegistry registry, List<ScaleSpecLibrary.QueryTarget> queries, int k) {
        if (queries.isEmpty()) {
            return 0.0;
        }
        int hits = 0;
        for (ScaleSpecLibrary.QueryTarget q : queries) {
            List<SpecEntry> top = registry.retrieve(q.query(), k);
            if (top.stream().anyMatch(e -> e.id().equals(q.targetId()))) {
                hits++;
            }
        }
        return (double) hits / queries.size();
    }

    /**
     * Precision@1 across the benchmark as {@code sequence} specs are added one at a time to
     * {@code core}. One point per added spec (plus the starting point), so the caller can sample
     * the curve.
     */
    public List<CurvePoint> growthCurve(List<SpecEntry> core, List<SpecEntry> sequence,
                                        SpecScorer scorer, List<ScaleSpecLibrary.QueryTarget> queries) {
        List<SpecEntry> library = new ArrayList<>(core);
        List<CurvePoint> points = new ArrayList<>();
        points.add(new CurvePoint(library.size(), precisionAt1(new ScaleSpecRegistry(library, scorer), queries)));
        for (SpecEntry s : sequence) {
            library.add(s);
            points.add(new CurvePoint(library.size(),
                    precisionAt1(new ScaleSpecRegistry(library, scorer), queries)));
        }
        return points;
    }

    /** For one benchmark query, the id of the top hit under {@code scorer} (or null if empty). */
    public String topHitId(ScaleSpecRegistry registry, String query) {
        List<SpecEntry> top = registry.retrieve(query, 1);
        return top.isEmpty() ? null : top.get(0).id();
    }
}
