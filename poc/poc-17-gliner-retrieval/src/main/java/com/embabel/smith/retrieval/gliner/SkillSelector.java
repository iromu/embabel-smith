package com.embabel.smith.retrieval.gliner;

import com.embabel.smith.retrieval.SkillEntry;

import java.util.List;

/**
 * Selects the single most relevant skill from a same-typed candidate set for a task query — the
 * "selector → top-1 over a candidate set" seam POC 17 introduces so its GLiNER2 transformer
 * classifier is directly comparable to POC 8's lexical and POC 12's dense scorers on identical
 * candidate sets.
 *
 * <p>A selector is a <em>batch</em> decision over the whole candidate set, not a per-entry scorer:
 * the transformer scores every candidate at once and returns the top hit, which is why this seam
 * (rather than POC 12's {@code SkillScorer}) is the common denominator.
 */
@FunctionalInterface
public interface SkillSelector {

    /**
     * Select the top-1 skill for {@code query} from {@code candidates}.
     *
     * @param query      the task query text
     * @param candidates the same-typed candidate set to choose from
     * @return the id of the top-ranked candidate, or {@code null} when {@code candidates} is empty
     */
    String select(String query, List<SkillEntry> candidates);
}
