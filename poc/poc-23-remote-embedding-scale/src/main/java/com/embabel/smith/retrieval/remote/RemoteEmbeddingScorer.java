package com.embabel.smith.retrieval.remote;

import com.embabel.smith.retrieval.SkillEntry;
import com.embabel.smith.retrieval.scale.SemanticSimilarity;
import com.embabel.smith.retrieval.scale.SkillScorer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link SkillScorer} backed by the remote {@link RemoteEmbedder}: cosine between the (memoised)
 * query embedding and each skill's (memoised) embedding. The same seam as POC 12's
 * {@code SemanticScorer} and POC 20's {@code RealEmbeddingScorer}, but the vectors come from a
 * network {@code EmbeddingService} rather than hashed n-grams or a local ONNX model — so the
 * comparison is apples-to-apples.
 *
 * <p>Both query and entry vectors are memoised by key, so a benchmark that scores the same query
 * against a growing library embeds each distinct query and each distinct skill exactly once.
 */
public final class RemoteEmbeddingScorer implements SkillScorer {

    private final RemoteEmbedder embedder;
    private final Map<String, double[]> queryVectors = new ConcurrentHashMap<>();
    private final Map<String, double[]> entryVectors = new ConcurrentHashMap<>();

    public RemoteEmbeddingScorer(RemoteEmbedder embedder) {
        this.embedder = embedder;
    }

    @Override
    public double score(String query, SkillEntry entry) {
        double[] q = queryVectors.computeIfAbsent(query, embedder::embed);
        double[] e = entryVectors.computeIfAbsent(entry.id(), k -> embedder.embed(text(entry)));
        return SemanticSimilarity.cosine(q, e);
    }

    /** The same text the lexical / dense baselines score, so the comparison is apples-to-apples. */
    private static String text(SkillEntry entry) {
        return entry.name() + " " + entry.description();
    }
}
