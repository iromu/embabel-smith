package com.embabel.smith.retrieval.scale;

import com.embabel.smith.retrieval.SkillEntry;

/**
 * The dense retriever: cosine over feature-hashing char/word n-gram embeddings. The comparison arm
 * in POC 12 — the offline stand-in for a real embedding backend.
 */
public final class SemanticScorer implements SkillScorer {

    @Override
    public double score(String query, SkillEntry entry) {
        String text = entry.name() + " " + entry.description();
        return SemanticSimilarity.cosine(SemanticSimilarity.embed(query), SemanticSimilarity.embed(text));
    }
}
