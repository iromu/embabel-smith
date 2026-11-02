package com.embabel.smith.retrieval.gliner;

import com.embabel.smith.retrieval.SkillEntry;
import io.gravitee.lab.gliner4j.schema.ClassificationLabel;
import io.gravitee.lab.gliner4j.schema.ClassificationResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link GlinerSelector} with a stubbed classifier (no model): it builds one label per
 * candidate (name = skill id, description = skill text), passes that label set to the classifier,
 * and returns the top-scored label's skill id — or no selection for an empty candidate set.
 */
class GlinerSelectorTest {

    private static SkillEntry entry(String id, String description) {
        return new SkillEntry(id, id, description, List.of("UserInput"), "BackgroundMessage");
    }

    @Test
    void buildsOneLabelPerCandidateWithIdAsNameAndDescriptionAsText() {
        List<SkillEntry> candidates = List.of(
                entry("coffee-machine", "Explains how a coffee machine works"),
                entry("toaster", "Explains how a toaster works"));
        assertThat(GlinerSelector.labelsFor(candidates)).containsExactly(
                new ClassificationLabel("coffee-machine", "Explains how a coffee machine works"),
                new ClassificationLabel("toaster", "Explains how a toaster works"));
    }

    @Test
    void passesTheCandidateLabelsToTheClassifier() {
        List<SkillEntry> candidates = List.of(entry("a", "desc a"), entry("b", "desc b"));
        List<ClassificationLabel> seen = new ArrayList<>();
        GlinerSelector selector = new GlinerSelector((text, labels, threshold) -> {
            seen.addAll(labels);
            return List.of(new ClassificationResult("a", 0.9f));
        });

        selector.select("query", candidates);

        assertThat(seen).containsExactly(
                new ClassificationLabel("a", "desc a"),
                new ClassificationLabel("b", "desc b"));
    }

    @Test
    void returnsTheTopScoredLabelAsTheSelection() {
        List<SkillEntry> candidates = List.of(entry("a", "desc a"), entry("b", "desc b"), entry("c", "desc c"));
        // The model "prefers" b (highest confidence), then a, then c.
        GlinerSelector selector = new GlinerSelector((text, labels, threshold) -> List.of(
                new ClassificationResult("b", 0.9f),
                new ClassificationResult("a", 0.5f),
                new ClassificationResult("c", 0.1f)));

        assertThat(selector.select("query", candidates)).isEqualTo("b");
    }

    @Test
    void returnsNullForEmptyCandidateSet() {
        GlinerSelector selector = new GlinerSelector((text, labels, threshold) -> List.of());
        assertThat(selector.select("query", List.of())).isNull();
    }
}
