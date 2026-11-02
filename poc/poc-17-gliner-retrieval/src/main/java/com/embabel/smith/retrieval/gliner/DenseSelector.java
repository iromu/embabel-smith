package com.embabel.smith.retrieval.gliner;

import com.embabel.smith.retrieval.SkillEntry;
import com.embabel.smith.retrieval.scale.SemanticSimilarity;

import java.util.List;

/**
 * The POC 12 dense baseline exposed as a {@link SkillSelector}: argmax over feature-hashing
 * char/word n-gram cosine similarity. Reused verbatim from POC 12 (via {@link SemanticSimilarity})
 * as the offline stand-in for a real embedding backend.
 */
public final class DenseSelector implements SkillSelector {

    @Override
    public String select(String query, List<SkillEntry> candidates) {
        if (candidates.isEmpty()) {
            return null;
        }
        double[] queryVec = SemanticSimilarity.embed(query);
        return candidates.stream()
                .max((a, b) -> Double.compare(score(queryVec, a), score(queryVec, b)))
                .map(SkillEntry::id)
                .orElse(null);
    }

    private static double score(double[] queryVec, SkillEntry entry) {
        String text = entry.name() + " " + entry.description();
        return SemanticSimilarity.cosine(queryVec, SemanticSimilarity.embed(text));
    }
}
