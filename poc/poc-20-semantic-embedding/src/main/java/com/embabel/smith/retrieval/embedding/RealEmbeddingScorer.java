package com.embabel.smith.retrieval.embedding;

import com.embabel.smith.retrieval.SkillEntry;
import com.embabel.smith.retrieval.scale.SkillScorer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link SkillScorer} backed by a real {@link Embedder}: cosine between the (memoised) query
 * embedding and each skill's (memoised) embedding. The cached vectors are the "semantic index" doc
 * 03 §2.1 describes. The only difference from POC 12's {@code SemanticScorer} is that the vectors
 * come from a trained transformer rather than hashed character/word n-grams — everything else (the
 * seam, the registry, the P@1 math) is reused verbatim, so the comparison is apples-to-apples.
 *
 * <p>Both query and entry vectors are memoised by key, so a benchmark that scores the same query
 * against a growing library embeds each distinct query and each distinct skill exactly once.
 */
public final class RealEmbeddingScorer implements SkillScorer {

    private final Embedder embedder;
    private final Map<String, float[]> queryVectors = new ConcurrentHashMap<>();
    private final Map<String, float[]> entryVectors = new ConcurrentHashMap<>();

    public RealEmbeddingScorer(Embedder embedder) {
        this.embedder = embedder;
    }

    @Override
    public double score(String query, SkillEntry entry) {
        float[] q = queryVectors.computeIfAbsent(query, embedder::embed);
        float[] e = entryVectors.computeIfAbsent(entry.id(), k -> embedder.embed(text(entry)));
        return EmbeddingMath.cosine(q, e);
    }

    /** The same text the lexical / dense baselines score, so the comparison is apples-to-apples. */
    private static String text(SkillEntry entry) {
        return entry.name() + " " + entry.description();
    }
}
