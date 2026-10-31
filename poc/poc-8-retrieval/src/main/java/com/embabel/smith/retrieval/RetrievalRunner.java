package com.embabel.smith.retrieval;

import java.util.ArrayList;
import java.util.List;

/**
 * The retrieval / shadowing measurement (POC 8). Pure and offline: it quantifies the skill
 * shadowing problem and shows the semantic pre-filter resolves it.
 *
 * <ul>
 *   <li>{@link #shadowingFactor} — how many same-typed skills a type-based router must consider.</li>
 *   <li>{@link #precisionAt1} — how often the pre-filter returns the right skill first.</li>
 *   <li>{@link #growthCurve} — precision@1 for a target query as same-typed distractors are added.</li>
 * </ul>
 */
public class RetrievalRunner {

    /** A point on the growth curve: library size and whether the target was retrieved first. */
    public record GrowthPoint(int librarySize, boolean targetTop1) {
    }

    /**
     * The shadowing factor for a task needing {@code inputTypes -> outputType}: the count of skills
     * a pure type-based (GOAP) router would treat as candidates before any semantic narrowing.
     */
    public int shadowingFactor(SkillRegistry registry, List<String> inputTypes, String outputType) {
        return registry.typeCompatibleCount(inputTypes, outputType);
    }

    /**
     * precision@1 across a set of benchmark queries: the fraction for which the pre-filter returns
     * the expected skill as the top hit.
     */
    public double precisionAt1(SkillRegistry registry, List<SampleSkillLibrary.QueryTarget> queries) {
        if (queries.isEmpty()) {
            return 0.0;
        }
        int hits = 0;
        for (SampleSkillLibrary.QueryTarget q : queries) {
            List<SkillEntry> top = registry.retrieve(q.query(), 1);
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
    public List<GrowthPoint> growthCurve(List<SkillEntry> base, List<SkillEntry> distractors,
                                         String query, String targetId) {
        List<SkillEntry> current = new ArrayList<>(base);
        List<GrowthPoint> points = new ArrayList<>();
        points.add(measure(current, query, targetId));
        for (SkillEntry d : distractors) {
            current.add(d);
            points.add(measure(current, query, targetId));
        }
        return points;
    }

    private GrowthPoint measure(List<SkillEntry> library, String query, String targetId) {
        SkillRegistry registry = new SkillRegistry(library);
        List<SkillEntry> top = registry.retrieve(query, 1);
        boolean hit = !top.isEmpty() && top.get(0).id().equals(targetId);
        return new GrowthPoint(library.size(), hit);
    }
}
