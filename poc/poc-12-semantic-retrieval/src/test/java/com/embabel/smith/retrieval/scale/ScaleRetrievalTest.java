package com.embabel.smith.retrieval.scale;

import com.embabel.smith.retrieval.SkillEntry;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline proof of the at-scale retrieval claim (POC 12): retrieval is clean on distinct topics at
 * 200+ skills, but same-topic near-duplicates shadow the canonical target — the selection failure
 * the literature attributes to skill shadowing.
 */
class ScaleRetrievalTest {

    private final ScaleRetrievalRunner runner = new ScaleRetrievalRunner();
    private final List<ScaleSkillLibrary.QueryTarget> queries = ScaleSkillLibrary.benchmark();
    private final List<SkillEntry> core = ScaleSkillLibrary.core();
    private final List<SkillEntry> full = ScaleSkillLibrary.full();

    @Test
    void theLibraryReachesTheTwoHundredSkillScale() {
        assertThat(core.size()).isGreaterThan(150);
        assertThat(full.size()).isGreaterThan(200);
    }

    @Test
    void everySkillIsSameTypedSoTypeMatchingCannotDisambiguate() {
        assertThat(full).allSatisfy(e -> {
            assertThat(e.inputTypes()).containsExactly(ScaleSkillLibrary.USER_INPUT);
            assertThat(e.outputType()).isEqualTo(ScaleSkillLibrary.BACKGROUND_MESSAGE);
        });
    }

    @Test
    void retrievalIsCleanOnDistinctTopicsForBothScorers() {
        assertThat(runner.precisionAt1(new ScaleSkillRegistry(core, new LexicalScorer()), queries))
                .as("lexical resolves every distinct-topic target")
                .isEqualTo(1.0);
        assertThat(runner.precisionAt1(new ScaleSkillRegistry(core, new SemanticScorer()), queries))
                .as("dense resolves every distinct-topic target")
                .isEqualTo(1.0);
    }

    @Test
    void nearDuplicatesShadowTheCanonicalTarget() {
        double lexical = runner.precisionAt1(new ScaleSkillRegistry(full, new LexicalScorer()), queries);
        assertThat(lexical)
                .as("some canonical target is no longer top-1 once same-topic variants accumulate")
                .isLessThan(1.0);
    }

    @Test
    void theGrowthCurveStartsCleanAndDegradesAsShadowingAccumulates() {
        List<ScaleRetrievalRunner.CurvePoint> curve =
                runner.growthCurve(core, ScaleSkillLibrary.growthSequence(), new LexicalScorer(), queries);
        assertThat(curve.get(0).precisionAt1()).as("clean at core scale").isEqualTo(1.0);
        assertThat(curve.get(curve.size() - 1).precisionAt1())
                .as("degraded once the same-topic near-duplicates are all present")
                .isLessThan(1.0);
    }
}
