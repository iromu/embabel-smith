package com.embabel.smith.retrieval.gliner;

import com.embabel.smith.retrieval.LexicalSimilarity;
import com.embabel.smith.retrieval.SkillEntry;

import java.util.List;
import java.util.Map;

/**
 * The POC 8 lexical baseline exposed as a {@link SkillSelector}: argmax over word term-frequency
 * cosine similarity. Reused verbatim from POC 8 (via {@link LexicalSimilarity}) so the POC 17
 * three-method comparison is apples-to-apples with POC 12.
 */
public final class LexicalSelector implements SkillSelector {

    @Override
    public String select(String query, List<SkillEntry> candidates) {
        if (candidates.isEmpty()) {
            return null;
        }
        Map<String, Integer> queryTf = LexicalSimilarity.tf(LexicalSimilarity.tokenize(query));
        return candidates.stream()
                .max((a, b) -> Double.compare(score(queryTf, a), score(queryTf, b)))
                .map(SkillEntry::id)
                .orElse(null);
    }

    private static double score(Map<String, Integer> queryTf, SkillEntry entry) {
        String text = entry.name() + " " + entry.description();
        return LexicalSimilarity.cosine(queryTf, LexicalSimilarity.tf(LexicalSimilarity.tokenize(text)));
    }
}
