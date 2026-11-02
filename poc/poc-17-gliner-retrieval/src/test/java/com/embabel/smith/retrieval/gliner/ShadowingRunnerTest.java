package com.embabel.smith.retrieval.gliner;

import com.embabel.smith.retrieval.SkillEntry;
import com.embabel.smith.retrieval.scale.ScaleSkillLibrary;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the {@link ShadowingRunner} Precision@1 math with stubbed selectors: a perfect selector
 * scores 1.0, an always-shadowed selector scores 0.0, a half-right selector scores the fraction of
 * canonical hits, and the result is deterministic. Also pins the two candidate-set variants.
 */
class ShadowingRunnerTest {

    private static final SkillSelector ALWAYS_CANONICAL =
            (query, candidates) -> candidates.stream()
                    .filter(e -> !isNearDup(e.id())).findFirst().map(SkillEntry::id).orElse(null);

    private static final SkillSelector ALWAYS_NEAR_DUP =
            (query, candidates) -> candidates.stream()
                    .filter(e -> isNearDup(e.id())).findFirst().map(SkillEntry::id).orElse(null);

    private static boolean isNearDup(String id) {
        return id.matches(".*-v\\d+$");
    }

    @Test
    void perfectSelectorScoresOnePointZero() {
        ShadowingRunner runner = new ShadowingRunner();
        assertThat(runner.precisionAt1(ALWAYS_CANONICAL, ShadowingRunner.Variant.MATCHING)).isEqualTo(1.0);
        assertThat(runner.precisionAt1(ALWAYS_CANONICAL, ShadowingRunner.Variant.STRICT)).isEqualTo(1.0);
    }

    @Test
    void alwaysShadowedSelectorScoresZero() {
        ShadowingRunner runner = new ShadowingRunner();
        assertThat(runner.precisionAt1(ALWAYS_NEAR_DUP, ShadowingRunner.Variant.MATCHING)).isEqualTo(0.0);
        assertThat(runner.precisionAt1(ALWAYS_NEAR_DUP, ShadowingRunner.Variant.STRICT)).isEqualTo(0.0);
    }

    @Test
    void partialSelectorScoresTheFractionOfCanonicalHits() {
        List<ScaleSkillLibrary.QueryTarget> targets = ScaleSkillLibrary.benchmark();
        Set<String> canonicalFor = targets.subList(0, 3).stream()
                .map(ScaleSkillLibrary.QueryTarget::targetId)
                .collect(Collectors.toSet());
        SkillSelector halfRight = (query, candidates) -> {
            SkillEntry canonical = candidates.stream().filter(e -> !isNearDup(e.id())).findFirst().orElseThrow();
            if (canonicalFor.contains(canonical.id())) {
                return canonical.id();
            }
            return candidates.stream().filter(e -> isNearDup(e.id())).findFirst().orElseThrow().id();
        };
        ShadowingRunner runner = new ShadowingRunner(targets);
        assertThat(runner.precisionAt1(halfRight, ShadowingRunner.Variant.MATCHING)).isEqualTo(0.5);
        assertThat(runner.precisionAt1(halfRight, ShadowingRunner.Variant.STRICT)).isEqualTo(0.5);
    }

    @Test
    void precisionAt1IsDeterministic() {
        ShadowingRunner runner = new ShadowingRunner();
        double first = runner.precisionAt1(ALWAYS_CANONICAL, ShadowingRunner.Variant.MATCHING);
        for (int i = 0; i < 5; i++) {
            assertThat(runner.precisionAt1(ALWAYS_CANONICAL, ShadowingRunner.Variant.MATCHING)).isEqualTo(first);
        }
    }

    @Test
    void emptyTargetSetScoresZero() {
        assertThat(new ShadowingRunner(List.of())
                .precisionAt1(ALWAYS_CANONICAL, ShadowingRunner.Variant.MATCHING)).isEqualTo(0.0);
    }

    @Test
    void matchingIncludesAllNearDuplicatesAndStrictExcludesTheNearExactOne() {
        ShadowingRunner runner = new ShadowingRunner();
        String target = ScaleSkillLibrary.benchmark().get(0).targetId();
        List<SkillEntry> matching = runner.candidatesFor(target, ShadowingRunner.Variant.MATCHING);
        List<SkillEntry> strict = runner.candidatesFor(target, ShadowingRunner.Variant.STRICT);

        // canonical + 15 near-dups = 16; strict drops the near-exact v0 = 15.
        assertThat(matching).hasSize(16);
        assertThat(strict).hasSize(15);
        assertThat(matching).anySatisfy(e -> assertThat(e.id()).isEqualTo(target + "-v0"));
        assertThat(strict).noneSatisfy(e -> assertThat(e.id()).isEqualTo(target + "-v0"));
        assertThat(matching).anySatisfy(e -> assertThat(e.id()).isEqualTo(target));
        assertThat(strict).anySatisfy(e -> assertThat(e.id()).isEqualTo(target));
    }
}
