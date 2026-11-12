package com.embabel.smith.retrieval.remote;

import com.embabel.smith.retrieval.SpecEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteEmbeddingScorerTest {

    @Test
    void ranksTheCloserEmbeddingHigher() {
        RemoteEmbedder embedder = StubEmbedding.embedder(text -> {
            if (text.contains("match")) {
                return new double[]{1, 0};
            }
            if (text.contains("other")) {
                return new double[]{0, 1};
            }
            return new double[]{1, 0}; // the query
        });
        RemoteEmbeddingScorer scorer = new RemoteEmbeddingScorer(embedder);
        SpecEntry match = entry("match");
        SpecEntry other = entry("other");
        assertThat(scorer.score("q", match)).isGreaterThan(scorer.score("q", other));
    }

    @Test
    void memoisesSoRepeatedScoringDoesNotReEmbed() {
        RemoteEmbedder embedder = StubEmbedding.embedder(text -> new double[]{1, 0});
        RemoteEmbeddingScorer scorer = new RemoteEmbeddingScorer(embedder);
        SpecEntry e = entry("e");
        scorer.score("q", e);
        long afterFirst = embedder.requestCount(); // query + entry = 2 distinct texts
        assertThat(afterFirst).isEqualTo(2);
        for (int i = 0; i < 5; i++) {
            scorer.score("q", e);
        }
        assertThat(embedder.requestCount()).isEqualTo(afterFirst);
    }

    private static SpecEntry entry(String name) {
        return new SpecEntry(name, name, name, List.of("UserInput"), "BackgroundMessage");
    }
}
