package com.embabel.smith.ui;

import com.embabel.smith.lifecycle.LifecycleState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline tests for the library curation (POC 15): the pure content-hash / drift / state-machine
 * half, reusing POC 18's {@code LifecycleManager}. A skill's contract is its eval pass-rate; the
 * curation re-derives its lifecycle from the recorded baseline, the live content hash, and the
 * latest measured score. No model needed — the scores are plain inputs.
 */
class SkillCurationTest {

    private SkillCuration curation;
    private String skill;

    @BeforeEach
    void setUp() {
        SkillCatalog catalog = new SkillCatalog("");
        curation = new SkillCuration(catalog);
        skill = catalog.all().keySet().iterator().next();
    }

    @Test
    void aFreshCatalogSkillIsActiveAndSurfaced() {
        assertThat(curation.currentState(skill)).isEqualTo(LifecycleState.ACTIVE);
        assertThat(curation.surfaced(curation.currentState(skill))).isTrue();
        assertThat(curation.contentDrift(skill)).isFalse();
    }

    @Test
    void aCleanReEvalKeepsTheSkillActive() {
        assertThat(curation.curate(skill, 1.0)).isEqualTo(LifecycleState.ACTIVE);
    }

    @Test
    void aScoreDropDegradesAndDeRanksTheSkill() {
        LifecycleState state = curation.curate(skill, 0.85);
        assertThat(state).isEqualTo(LifecycleState.DEGRADED);
        assertThat(curation.surfaced(state)).isTrue(); // still in the library, just de-ranked
        assertThat(curation.deRankFactor(state)).isEqualTo(0.5);
    }

    @Test
    void aScoreCollapseRetiresTheSkillOutOfTheLibrary() {
        LifecycleState state = curation.curate(skill, 0.1);
        assertThat(state).isEqualTo(LifecycleState.RETIRED);
        assertThat(curation.surfaced(state)).isFalse(); // dropped from retrieval
    }

    @Test
    void retirementIsTerminal() {
        curation.curate(skill, 0.1); // retire
        assertThat(curation.curate(skill, 1.0)).isEqualTo(LifecycleState.RETIRED);
    }

    @Test
    void deRankFactorKeepsNonDegradedScoresIntact() {
        assertThat(curation.deRankFactor(LifecycleState.ACTIVE)).isEqualTo(1.0);
        assertThat(curation.deRankFactor(LifecycleState.INCUBATING)).isEqualTo(1.0);
        assertThat(curation.deRankFactor(LifecycleState.DEGRADED)).isEqualTo(0.5);
    }
}
