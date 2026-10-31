package com.embabel.smith.disclosure;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.common.model.SkillPackManifest;
import com.embabel.smith.common.samples.SampleSkillPacks;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline proof of the progressive-disclosure claim (POC 11): the plan context is bounded and
 * independent of the bundled reference size, while an eager (inlined) prompt grows with it.
 */
class ProgressiveDisclosureTest {

    private final ProgressiveDisclosureRunner runner = new ProgressiveDisclosureRunner();

    private SkillPack pack() {
        ActionStepSpec step = new ActionStepSpec(
                "action", "answer", "answer from the guide",
                List.of(SampleSkillPacks.USER_INPUT),
                SampleSkillPacks.BACKGROUND_MESSAGE,
                "Answer using the guide. Question: {{userInput.content}}",
                null, null, null, null, null, null, List.of());
        return new SkillPack(
                new SkillPackManifest("reference-heavy", "1.0.0"),
                GoalStepSpec.exportedGoal("domainAnswer", "answered", SampleSkillPacks.BACKGROUND_MESSAGE),
                List.of(step), List.of());
    }

    @Test
    void thePlanContextIsIndependentOfTheReferenceSize() {
        SkillPack pack = pack();
        int small = runner.measure(pack, "a tiny reference").planContextChars();
        int large = runner.measure(pack, "x".repeat(50_000)).planContextChars();
        assertThat(small).isEqualTo(large);
    }

    @Test
    void theEagerPromptGrowsWithTheReferenceButProgressiveDoesNot() {
        SkillPack pack = pack();
        ProgressiveDisclosureRunner.ContextCost small = runner.measure(pack, "ref");
        ProgressiveDisclosureRunner.ContextCost large = runner.measure(pack, "r".repeat(50_000));

        assertThat(large.eagerPromptChars()).isGreaterThan(small.eagerPromptChars());
        assertThat(large.progressivePromptChars()).isEqualTo(small.progressivePromptChars());
    }

    @Test
    void theEagerToProgressiveRatioGrowsWithTheReference() {
        SkillPack pack = pack();
        double smallRatio = runner.measure(pack, "ref").eagerToProgressiveRatio();
        double largeRatio = runner.measure(pack, "r".repeat(50_000)).eagerToProgressiveRatio();
        assertThat(largeRatio).isGreaterThan(smallRatio);
    }
}
