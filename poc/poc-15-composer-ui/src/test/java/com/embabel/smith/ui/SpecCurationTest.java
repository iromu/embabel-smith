package com.embabel.smith.ui;

import com.embabel.smith.lifecycle.LifecycleState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline tests for the library curation (POC 15): the pure content-hash / drift / state-machine
 * half, reusing POC 18's {@code LifecycleManager}. A spec's contract is its eval pass-rate; the
 * curation re-derives its lifecycle from the recorded baseline, the live content hash, and the
 * latest measured score. No model needed — the scores are plain inputs.
 */
class SpecCurationTest {

    private SpecCuration curation;
    private String spec;

    @BeforeEach
    void setUp() {
        SpecCatalog catalog = new SpecCatalog("");
        curation = new SpecCuration(catalog);
        spec = catalog.all().keySet().iterator().next();
    }

    @Test
    void aFreshCatalogSpecIsActiveAndSurfaced() {
        assertThat(curation.currentState(spec)).isEqualTo(LifecycleState.ACTIVE);
        assertThat(curation.surfaced(curation.currentState(spec))).isTrue();
        assertThat(curation.contentDrift(spec)).isFalse();
    }

    @Test
    void aCleanReEvalKeepsTheSpecActive() {
        assertThat(curation.curate(spec, 1.0)).isEqualTo(LifecycleState.ACTIVE);
    }

    @Test
    void aScoreDropDegradesAndDeRanksTheSpec() {
        LifecycleState state = curation.curate(spec, 0.85);
        assertThat(state).isEqualTo(LifecycleState.DEGRADED);
        assertThat(curation.surfaced(state)).isTrue(); // still in the library, just de-ranked
        assertThat(curation.deRankFactor(state)).isEqualTo(0.5);
    }

    @Test
    void aScoreCollapseRetiresTheSpecOutOfTheLibrary() {
        LifecycleState state = curation.curate(spec, 0.1);
        assertThat(state).isEqualTo(LifecycleState.RETIRED);
        assertThat(curation.surfaced(state)).isFalse(); // dropped from retrieval
    }

    @Test
    void retirementIsTerminal() {
        curation.curate(spec, 0.1); // retire
        assertThat(curation.curate(spec, 1.0)).isEqualTo(LifecycleState.RETIRED);
    }

    @Test
    void deRankFactorKeepsNonDegradedScoresIntact() {
        assertThat(curation.deRankFactor(LifecycleState.ACTIVE)).isEqualTo(1.0);
        assertThat(curation.deRankFactor(LifecycleState.INCUBATING)).isEqualTo(1.0);
        assertThat(curation.deRankFactor(LifecycleState.DEGRADED)).isEqualTo(0.5);
    }
}
