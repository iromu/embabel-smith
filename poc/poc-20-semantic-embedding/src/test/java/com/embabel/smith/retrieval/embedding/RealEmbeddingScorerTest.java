package com.embabel.smith.retrieval.embedding;

import com.embabel.smith.retrieval.SkillEntry;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Pins the scorer: cosine of the (memoised) embeddings, and that each distinct text is embedded once. */
class RealEmbeddingScorerTest {

    /** A deterministic {@link Embedder}: fixed unit vector per text, and a record of what it embedded. */
    private static final class CountingEmbedder implements Embedder {
        private final Map<String, float[]> byText = new HashMap<>();
        private final Set<String> embedded = new HashSet<>();

        CountingEmbedder() {
            byText.put("q coffee", new float[]{1, 0});
            byText.put("s1 coffee machine explainer", new float[]{1, 0});
            byText.put("s2 toaster explainer", new float[]{0, 1});
        }

        @Override
        public float[] embed(String text) {
            embedded.add(text);
            return byText.get(text);
        }
    }

    @Test
    void scoreIsTheCosineOfTheQueryAndEntryEmbeddings() {
        CountingEmbedder embedder = new CountingEmbedder();
        RealEmbeddingScorer scorer = new RealEmbeddingScorer(embedder);
        SkillEntry coffee = entry("s1", "coffee machine explainer");
        SkillEntry toaster = entry("s2", "toaster explainer");

        assertThat(scorer.score("q coffee", coffee)).isCloseTo(1.0, within(1e-6));
        assertThat(scorer.score("q coffee", toaster)).isCloseTo(0.0, within(1e-6));
    }

    @Test
    void eachDistinctQueryAndEntryIsEmbeddedOnlyOnce() {
        CountingEmbedder embedder = new CountingEmbedder();
        RealEmbeddingScorer scorer = new RealEmbeddingScorer(embedder);
        SkillEntry coffee = entry("s1", "coffee machine explainer");
        SkillEntry toaster = entry("s2", "toaster explainer");

        for (int i = 0; i < 3; i++) {
            scorer.score("q coffee", coffee);
            scorer.score("q coffee", toaster);
        }

        // one distinct query + two distinct entries, despite six score() calls.
        assertThat(embedder.embedded).containsExactlyInAnyOrder(
                "q coffee", "s1 coffee machine explainer", "s2 toaster explainer");
    }

    private static SkillEntry entry(String id, String description) {
        return new SkillEntry(id, id, description, List.of("UserInput"), "BackgroundMessage");
    }
}
