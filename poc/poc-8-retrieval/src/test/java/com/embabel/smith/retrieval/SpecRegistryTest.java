package com.embabel.smith.retrieval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline proof of the retrieval claim (POC 8): a same-typed library produces a large shadowing
 * factor, and the semantic pre-filter still routes each task to the right spec — including as
 * same-verb distractors are added.
 */
class SpecRegistryTest {

    private final RetrievalRunner runner = new RetrievalRunner();
    private final List<String> taskInputs = List.of(SampleSpecLibrary.USER_INPUT);
    private final String taskOutput = SampleSpecLibrary.BACKGROUND_MESSAGE;

    @Test
    void everySpecIsTypeCompatibleSoTypeMatchingCannotDisambiguate() {
        SpecRegistry registry = new SpecRegistry(SampleSpecLibrary.base());
        assertThat(runner.shadowingFactor(registry, taskInputs, taskOutput))
                .as("all specs share UserInput -> BackgroundMessage")
                .isEqualTo(registry.size());
    }

    @Test
    void thePreFilterRoutesEachBenchmarkTaskToTheRightSpec() {
        SpecRegistry registry = new SpecRegistry(SampleSpecLibrary.base());
        assertThat(runner.precisionAt1(registry, SampleSpecLibrary.benchmarkQueries()))
                .as("the semantic pre-filter resolves the shadowing")
                .isEqualTo(1.0);
    }

    @Test
    void theCoffeeSpecStaysTopOneAsSameVerbDistractorsAreAdded() {
        List<SpecEntry> base = SampleSpecLibrary.base();
        List<SpecEntry> distractors = SampleSpecLibrary.withExplainDistractors()
                .subList(base.size(), SampleSpecLibrary.withExplainDistractors().size());

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
    void anOffTopicQueryDoesNotRetrieveTheCoffeeSpec() {
        SpecRegistry registry = new SpecRegistry(SampleSpecLibrary.base());
        List<SpecEntry> top = registry.retrieve("balance my checkbook from these transactions", 1);
        assertThat(top.get(0).id()).isNotEqualTo("coffee-explainer");
        assertThat(top.get(0).id()).isEqualTo("checkbook-balancer");
    }
}
