package com.embabel.smith.skillformat;

import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.common.samples.SampleSkillPacks;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies tasks 4.1 (offline part) and 4.2: an exported goal is the callable skill and its action
 * chain resolves to the goal's output type; a goal declared {@code export: false} is not exposed.
 */
class SkillSurfaceTest {

    private final SkillSurface surface = new SkillSurface();

    @Test
    void anExportedGoalIsACallableSkill() {
        SkillPack pack = SampleSkillPacks.explainer();

        assertThat(surface.isCallableSkill(pack)).isTrue();
        GoalStepSpec skill = surface.callableSkill(pack);
        assertThat(skill).isNotNull();
        assertThat(skill.name()).isEqualTo("explainerComplete");
        assertThat(skill.outputTypeName()).isEqualTo(SampleSkillPacks.BACKGROUND_MESSAGE);
    }

    @Test
    void aNonExportedGoalIsNotExposedAsACallableSkill() {
        SkillPack pack = SampleSkillPacks.explainer();
        SkillPack notExported = new SkillPack(pack.manifest(),
                new GoalStepSpec("goal", "explainerComplete", "A complete explainer",
                        SampleSkillPacks.BACKGROUND_MESSAGE, false),
                pack.steps(), pack.types());

        assertThat(surface.isCallableSkill(notExported)).isFalse();
        assertThat(surface.callableSkill(notExported)).isNull();
    }

    @Test
    void theActionChainResolvesFromEntryToTheGoalOutput() {
        SkillPack pack = SampleSkillPacks.explainer();

        List<com.embabel.smith.common.model.ActionStepSpec> chain = surface.actionChain(pack);

        assertThat(chain).extracting(com.embabel.smith.common.model.ActionStepSpec::name)
                .containsExactly("extractTopic", "writeExplainer");
        // the chain's final output is the goal's output type
        assertThat(chain.get(chain.size() - 1).outputTypeName())
                .isEqualTo(pack.goalOutputType());
    }
}
