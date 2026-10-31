package com.embabel.smith.retrieval.scale;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Offline checks for the dense feature-hashing similarity that backs the semantic scorer.
 */
class SemanticSimilarityTest {

    @Test
    void embedProducesAFixedLengthVector() {
        assertThat(SemanticSimilarity.embed("explain how a coffee machine works"))
                .hasSize(SemanticSimilarity.DIMS);
    }

    @Test
    void identicalTextsScoreOne() {
        double[] a = SemanticSimilarity.embed("explain how a coffee machine works");
        assertThat(SemanticSimilarity.cosine(a, a)).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void disjointTextsScoreNearZero() {
        double s = SemanticSimilarity.cosine(
                SemanticSimilarity.embed("coffee machine boiler portafilter"),
                SemanticSimilarity.embed("legal contract obligations deadlines"));
        assertThat(s).isLessThan(0.2);
    }

    @Test
    void emptyTextScoresZero() {
        assertThat(SemanticSimilarity.cosine(
                SemanticSimilarity.embed(""),
                SemanticSimilarity.embed("anything at all"))).isEqualTo(0.0);
    }

    @Test
    void subwordOverlapIsCapturedThatWholeWordTfMisses() {
        // "coffeemaker" and "coffee machine" share no whole word, but share character n-grams.
        double subword = SemanticSimilarity.cosine(
                SemanticSimilarity.embed("coffeemaker"),
                SemanticSimilarity.embed("coffee machine"));
        double unrelated = SemanticSimilarity.cosine(
                SemanticSimilarity.embed("toaster"),
                SemanticSimilarity.embed("coffee machine"));
        assertThat(subword)
                .as("character n-grams should link the sub-word forms")
                .isGreaterThan(unrelated);
    }
}
