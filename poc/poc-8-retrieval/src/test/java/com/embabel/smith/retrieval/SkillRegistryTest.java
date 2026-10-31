package com.embabel.smith.retrieval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline proof of the retrieval claim (POC 8): a same-typed library produces a large shadowing
 * factor, and the semantic pre-filter still routes each task to the right skill — including as
 * same-verb distractors are added.
 */
class SkillRegistryTest {

    private final RetrievalRunner runner = new RetrievalRunner();
    private final List<String> taskInputs = List.of(SampleSkillLibrary.USER_INPUT);
    private final String taskOutput = SampleSkillLibrary.BACKGROUND_MESSAGE;

    @Test
    void everySkillIsTypeCompatibleSoTypeMatchingCannotDisambiguate() {
        SkillRegistry registry = new SkillRegistry(SampleSkillLibrary.base());
        assertThat(runner.shadowingFactor(registry, taskInputs, taskOutput))
                .as("all skills share UserInput -> BackgroundMessage")
                .isEqualTo(registry.size());
    }

    @Test
    void thePreFilterRoutesEachBenchmarkTaskToTheRightSkill() {
        SkillRegistry registry = new SkillRegistry(SampleSkillLibrary.base());
        assertThat(runner.precisionAt1(registry, SampleSkillLibrary.benchmarkQueries()))
                .as("the semantic pre-filter resolves the shadowing")
                .isEqualTo(1.0);
    }

    @Test
    void theCoffeeSkillStaysTopOneAsSameVerbDistractorsAreAdded() {
        List<SkillEntry> base = SampleSkillLibrary.base();
        List<SkillEntry> distractors = SampleSkillLibrary.withExplainDistractors()
                .subList(base.size(), SampleSkillLibrary.withExplainDistractors().size());

        List<RetrievalRunner.GrowthPoint> curve = runner.growthCurve(
                base, distractors, "explain how a coffee machine works in plain language", "coffee-explainer");

        // one point for the base library plus one per added distractor
        assertThat(curve).hasSize(distractors.size() + 1);
        assertThat(curve)
                .allSatisfy(point ->
                        assertThat(point.targetTop1())
                                .as("coffee-explainer must stay top-1 at library size " + point.librarySize())
                                .isTrue());
    }

    @Test
    void anOffTopicQueryDoesNotRetrieveTheCoffeeSkill() {
        SkillRegistry registry = new SkillRegistry(SampleSkillLibrary.base());
        List<SkillEntry> top = registry.retrieve("balance my checkbook from these transactions", 1);
        assertThat(top.get(0).id()).isNotEqualTo("coffee-explainer");
        assertThat(top.get(0).id()).isEqualTo("checkbook-balancer");
    }
}
