package com.embabel.smith.retrieval.gliner;

import com.embabel.smith.retrieval.SpecEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link DenseSelector} returns the correct top-1 id on a small candidate set: the spec
 * whose dense (char/word n-gram) embedding best matches the query wins, and an empty set yields no
 * selection.
 */
class DenseSelectorTest {

    private final DenseSelector selector = new DenseSelector();

    private static SpecEntry entry(String id, String description) {
        return new SpecEntry(id, id, description, List.of("UserInput"), "BackgroundMessage");
    }

    @Test
    void picksTheBestDenseMatch() {
        List<SpecEntry> candidates = List.of(
                entry("legal-contract", "Summarizes a legal contract into plain language, with the key obligations and deadlines"),
                entry("lease-agreement", "Summarizes a lease agreement into plain language"),
                entry("news-article", "Summarizes a news article into plain language"));
        assertThat(selector.select("summarize this legal contract", candidates))
                .isEqualTo("legal-contract");
    }

    @Test
    void returnsNullForEmptyCandidateSet() {
        assertThat(selector.select("summarize this legal contract", List.of())).isNull();
    }
}
