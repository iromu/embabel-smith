package com.embabel.smith.common.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline test of the {@link DraftQuality} goal-shape classifier (doc 02 caveat #2 — the measurable
 * half of "LLM-authored spec quality is weak at the source"). Each degenerate goal shape is
 * detected with the right verdict and reason, and a coherent draft is reported healthy. Pure
 * function — no LLM, no platform.
 */
class DraftQualityTest {

    private static final String USER_INPUT = "com.embabel.agent.domain.io.UserInput";
    private static final String BACKGROUND_MESSAGE = "com.embabel.agent.spec.model.BackgroundMessage";

    @Test
    void aCoherentDraftIsHealthy() {
        // UserInput -> extractTopic -> TopicBrief -> writeExplainer -> BackgroundMessage (goal).
        DraftQuality.Assessment a = DraftQuality.assess(BACKGROUND_MESSAGE,
                List.of(USER_INPUT + "ignored", "TopicBrief", BACKGROUND_MESSAGE));
        assertThat(a.verdict()).isEqualTo(DraftQuality.Verdict.HEALTHY);
        assertThat(a.isDegenerate()).isFalse();
        assertThat(a.reason()).contains("produced by an action");
    }

    @Test
    void aMissingGoalIsDegenerate() {
        DraftQuality.Assessment a = DraftQuality.assess(null, List.of("TopicBrief"));
        assertThat(a.verdict()).isEqualTo(DraftQuality.Verdict.NO_GOAL);
        assertThat(a.isDegenerate()).isTrue();
    }

    @Test
    void aVoidGoalIsDegenerate() {
        DraftQuality.Assessment a = DraftQuality.assess("void", List.of("void"));
        assertThat(a.verdict()).isEqualTo(DraftQuality.Verdict.VOID_GOAL);
        assertThat(a.isDegenerate()).isTrue();
        assertThat(a.reason()).contains("void");
    }

    @Test
    void aNoOpGoalThatEchoesItsInputIsDegenerate() {
        DraftQuality.Assessment a = DraftQuality.assess(USER_INPUT, List.of(USER_INPUT));
        assertThat(a.verdict()).isEqualTo(DraftQuality.Verdict.NO_OP_GOAL);
        assertThat(a.isDegenerate()).isTrue();
        assertThat(a.reason()).contains("echoes its input");
    }

    @Test
    void aScalarGoalOutputIsDegenerate() {
        DraftQuality.Assessment a = DraftQuality.assess("string", List.of("string"));
        assertThat(a.verdict()).isEqualTo(DraftQuality.Verdict.SCALAR_GOAL);
        assertThat(a.isDegenerate()).isTrue();
        assertThat(a.reason()).contains("scalar");
    }

    @Test
    void anOrphanGoalWhoseOutputNoActionProducesIsDegenerate() {
        // The goal outputs BackgroundMessage, but the only action produces TopicBrief — the plan
        // cannot reach the goal, so it STUCKs. This is the shape the normalizer does not auto-fix.
        DraftQuality.Assessment a = DraftQuality.assess(BACKGROUND_MESSAGE, List.of("TopicBrief"));
        assertThat(a.verdict()).isEqualTo(DraftQuality.Verdict.ORPHAN_GOAL);
        assertThat(a.isDegenerate()).isTrue();
        assertThat(a.reason()).contains("no action produces");
    }
}
