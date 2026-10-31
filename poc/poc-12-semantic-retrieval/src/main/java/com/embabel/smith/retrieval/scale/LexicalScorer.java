package com.embabel.smith.retrieval.scale;

import com.embabel.smith.retrieval.LexicalSimilarity;
import com.embabel.smith.retrieval.SkillEntry;

/**
 * The POC 8 baseline: cosine over word term-frequency vectors. Exact whole-word overlap only — the
 * "lexical" retriever the literature says degrades as same-typed skills accumulate. Reused verbatim
 * from POC 8 so the comparison is apples-to-apples.
 */
public final class LexicalScorer implements SkillScorer {

    @Override
    public double score(String query, SkillEntry entry) {
        String text = entry.name() + " " + entry.description();
        return LexicalSimilarity.cosine(
                LexicalSimilarity.tf(LexicalSimilarity.tokenize(query)),
                LexicalSimilarity.tf(LexicalSimilarity.tokenize(text)));
    }
}
