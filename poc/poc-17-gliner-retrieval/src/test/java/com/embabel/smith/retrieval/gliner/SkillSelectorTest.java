package com.embabel.smith.retrieval.gliner;

import com.embabel.smith.retrieval.SkillEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the {@link SkillSelector} top-1 contract on a trivial candidate set: a selector returns
 * exactly one id drawn from the candidate set, and no selection (null) for an empty set.
 */
class SkillSelectorTest {

    private static SkillEntry entry(String id) {
        return new SkillEntry(id, id, id + " does something", List.of("UserInput"), "BackgroundMessage");
    }

    /** A trivial selector: the candidate whose name equals the query, else the first, else null. */
    private static final SkillSelector PICK_BY_NAME =
            (query, candidates) -> candidates.stream()
                    .filter(c -> c.name().equals(query))
                    .map(SkillEntry::id)
                    .findFirst()
                    .orElseGet(() -> candidates.isEmpty() ? null : candidates.get(0).id());

    @Test
    void returnsNullForEmptyCandidateSet() {
        assertThat(PICK_BY_NAME.select("anything", List.of())).isNull();
    }

    @Test
    void returnsExactlyOneTopHitDrawnFromTheSet() {
        List<SkillEntry> candidates = List.of(entry("a"), entry("b"), entry("c"));
        String top = PICK_BY_NAME.select("b", candidates);
        assertThat(top).isEqualTo("b");
        assertThat(top).isIn("a", "b", "c");
    }
}
