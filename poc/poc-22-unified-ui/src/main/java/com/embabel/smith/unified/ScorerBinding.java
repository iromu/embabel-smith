package com.embabel.smith.unified;

import com.embabel.smith.retrieval.scale.SkillScorer;

/**
 * The active retrieval scorer and its display name — the shared {@link SkillScorer} seam the
 * Retrieve and Scale panes drive. The default is the offline lexical scorer; a denser scorer
 * (POC 17/19/20) drops in by supplying a different {@code ScorerBinding} bean (for POC 20,
 * {@code builder.scorer=embedding}), without changing the panes' interface (the response carries
 * the active scorer name).
 */
public record ScorerBinding(SkillScorer scorer, String name) {}
