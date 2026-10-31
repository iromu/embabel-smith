package com.embabel.smith.retrieval.scale;

import com.embabel.smith.retrieval.SkillEntry;

/**
 * Ranks a skill against a task query for the retrieval pre-filter. A retriever is defined by its
 * scorer, so the same {@link ScaleSkillRegistry} can be driven by the lexical baseline (POC 8) or
 * a denser semantic scorer — the comparison POC 12 makes at scale.
 */
@FunctionalInterface
public interface SkillScorer {

    /** A relevance score for {@code entry} against {@code query}; higher is more relevant. */
    double score(String query, SkillEntry entry);
}
