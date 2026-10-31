package com.embabel.smith.retrieval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Offline checks for the lexical similarity scorer that backs the pre-filter.
 */
class LexicalSimilarityTest {

    @Test
    void tokenizeLowercasesSplitsAndDropsShortTokens() {
        assertThat(LexicalSimilarity.tokenize("Coffee Machine works! a"))
                .containsExactly("coffee", "machine", "works");
    }

    @Test
    void identicalTextsScoreOne() {
        List<String> tokens = LexicalSimilarity.tokenize("explain how a coffee machine works");
        assertThat(LexicalSimilarity.cosine(
                LexicalSimilarity.tf(tokens), LexicalSimilarity.tf(tokens)))
                .isCloseTo(1.0, within(1e-9));
    }

    @Test
    void disjointTextsScoreZero() {
        assertThat(LexicalSimilarity.cosine(
                LexicalSimilarity.tf(LexicalSimilarity.tokenize("coffee machine")),
                LexicalSimilarity.tf(LexicalSimilarity.tokenize("legal contract")))).isEqualTo(0.0);
    }

    @Test
    void emptyTextScoresZero() {
        assertThat(LexicalSimilarity.cosine(
                LexicalSimilarity.tf(List.of()),
                LexicalSimilarity.tf(LexicalSimilarity.tokenize("anything")))).isEqualTo(0.0);
    }
}
