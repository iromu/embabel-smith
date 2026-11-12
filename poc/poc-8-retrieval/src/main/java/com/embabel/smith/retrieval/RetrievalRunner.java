package com.embabel.smith.retrieval;

import java.util.ArrayList;
import java.util.List;

/**
 * The retrieval / shadowing measurement (POC 8). Pure and offline: it quantifies the spec
 * shadowing problem and shows the semantic pre-filter resolves it.
 *
 * <ul>
 *   <li>{@link #shadowingFactor} — how many same-typed specs a type-based router must consider.</li>
 *   <li>{@link #precisionAt1} — how often the pre-filter returns the right spec first.</li>
 *   <li>{@link #growthCurve} — precision@1 for a target query as same-typed distractors are added.</li>
 * </ul>
 */
public class RetrievalRunner {

    /** A point on the growth curve: library size and whether the target was retrieved first. */
    public record GrowthPoint(int librarySize, boolean targetTop1) {
    }

    /**
     * The shadowing factor for a task needing {@code inputTypes -> outputType}: the count of specs
     * a pure type-based (GOAP) router would treat as candidates before any semantic narrowing.
     */
    public int shadowingFactor(SpecRegistry registry, List<String> inputTypes, String outputType) {
        return registry.typeCompatibleCount(inputTypes, outputType);
    }

    /**
     * precision@1 across a set of benchmark queries: the fraction for which the pre-filter returns
     * the expected spec as the top hit.
     */
    public double precisionAt1(SpecRegistry registry, List<SampleSpecLibrary.QueryTarget> queries) {
        if (queries.isEmpty()) {
            return 0.0;
        }
        int hits = 0;
        for (SampleSpecLibrary.QueryTarget q : queries) {
            List<SpecEntry> top = registry.retrieve(q.query(), 1);
            if (!top.isEmpty() && top.get(0).id().equals(q.targetId())) {
                hits++;
            }
        }
        return (double) hits / queries.size();
    }

    /**
     * The growth curve for a single target query: start from {@code base}, add {@code distractors}
     * one at a time, and record whether {@code targetId} is still retrieved first at each size. This
     * is the "selection accuracy as the library grows" measurement from the POC brief.
     */
    public List<GrowthPoint> growthCurve(List<SpecEntry> base, List<SpecEntry> distractors,
                                         String query, String targetId) {
        List<SpecEntry> current = new ArrayList<>(base);
        List<GrowthPoint> points = new ArrayList<>();
        points.add(measure(current, query, targetId));
        for (SpecEntry d : distractors) {
            current.add(d);
            points.add(measure(current, query, targetId));
        }
        return points;
    }

    private GrowthPoint measure(List<SpecEntry> library, String query, String targetId) {
        SpecRegistry registry = new SpecRegistry(library);
        List<SpecEntry> top = registry.retrieve(query, 1);
        boolean hit = !top.isEmpty() && top.get(0).id().equals(targetId);
        return new GrowthPoint(library.size(), hit);
    }
}
