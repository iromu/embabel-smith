package com.embabel.smith.retrieval.scale;

import com.embabel.smith.retrieval.SpecEntry;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline proof of the at-scale retrieval claim (POC 12): retrieval is clean on distinct topics at
 * 200+ specs, but same-topic near-duplicates shadow the canonical target — the selection failure
 * the literature attributes to spec shadowing.
 */
class ScaleRetrievalTest {

    private final ScaleRetrievalRunner runner = new ScaleRetrievalRunner();
    private final List<ScaleSpecLibrary.QueryTarget> queries = ScaleSpecLibrary.benchmark();
    private final List<SpecEntry> core = ScaleSpecLibrary.core();
    private final List<SpecEntry> full = ScaleSpecLibrary.full();

    @Test
    void theLibraryReachesTheTwoHundredSpecScale() {
        assertThat(core.size()).isGreaterThan(150);
        assertThat(full.size()).isGreaterThan(200);
    }

    @Test
    void everySpecIsSameTypedSoTypeMatchingCannotDisambiguate() {
        assertThat(full).allSatisfy(e -> {
            assertThat(e.inputTypes()).containsExactly(ScaleSpecLibrary.USER_INPUT);
            assertThat(e.outputType()).isEqualTo(ScaleSpecLibrary.BACKGROUND_MESSAGE);
        });
    }

    @Test
    void retrievalIsCleanOnDistinctTopicsForBothScorers() {
        assertThat(runner.precisionAt1(new ScaleSpecRegistry(core, new LexicalScorer()), queries))
                .as("lexical resolves every distinct-topic target")
                .isEqualTo(1.0);
        assertThat(runner.precisionAt1(new ScaleSpecRegistry(core, new SemanticScorer()), queries))
                .as("dense resolves every distinct-topic target")
                .isEqualTo(1.0);
    }

    @Test
    void nearDuplicatesShadowTheCanonicalTarget() {
        double lexical = runner.precisionAt1(new ScaleSpecRegistry(full, new LexicalScorer()), queries);
        assertThat(lexical)
                .as("some canonical target is no longer top-1 once same-topic variants accumulate")
                .isLessThan(1.0);
    }

    @Test
    void theGrowthCurveStartsCleanAndDegradesAsShadowingAccumulates() {
        List<ScaleRetrievalRunner.CurvePoint> curve =
                runner.growthCurve(core, ScaleSpecLibrary.growthSequence(), new LexicalScorer(), queries);
        assertThat(curve.get(0).precisionAt1()).as("clean at core scale").isEqualTo(1.0);
        assertThat(curve.get(curve.size() - 1).precisionAt1())
                .as("degraded once the same-topic near-duplicates are all present")
                .isLessThan(1.0);
    }
}
