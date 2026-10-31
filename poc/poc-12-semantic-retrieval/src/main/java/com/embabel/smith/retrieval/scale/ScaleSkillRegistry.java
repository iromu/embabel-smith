package com.embabel.smith.retrieval.scale;

import com.embabel.smith.retrieval.SkillEntry;

import java.util.Comparator;
import java.util.List;

/**
 * A scorer-agnostic skill registry: ranks {@link SkillEntry} by a pluggable {@link SkillScorer} and
 * returns the top-k. The same registry drives both the lexical baseline and the dense semantic
 * retriever, so the scale comparison in POC 12 is apples-to-apples.
 */
public class ScaleSkillRegistry {

    private final List<SkillEntry> entries;
    private final SkillScorer scorer;

    public ScaleSkillRegistry(List<SkillEntry> entries, SkillScorer scorer) {
        this.entries = List.copyOf(entries);
        this.scorer = scorer;
    }

    public int size() {
        return entries.size();
    }

    /** Rank skills by the scorer's relevance to {@code query} and return the top {@code k}. */
    public List<SkillEntry> retrieve(String query, int k) {
        return entries.stream()
                .map(e -> new Scored(e, scorer.score(query, e)))
                .sorted(Comparator.comparingDouble((Scored s) -> s.score).reversed())
                .limit(Math.max(0, k))
                .map(Scored::entry)
                .toList();
    }

    private record Scored(SkillEntry entry, double score) {
    }
}
