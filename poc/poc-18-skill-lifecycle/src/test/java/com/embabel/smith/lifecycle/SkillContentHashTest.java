package com.embabel.smith.lifecycle;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.DynamicTypeDef;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.model.PropertyDef;
import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.common.model.SkillPackManifest;
import com.embabel.smith.common.samples.SampleSkillPacks;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Offline checks for the content hash: it is stable for an unchanged pack and changes when any
 * load-bearing surface (prompt, referenced type, tool group) changes — the content-drift signal.
 */
class SkillContentHashTest {

    @Test
    void isStableForAnUnchangedPack() {
        SkillPack pack = LifecycleSkill.healthy();
        assertThat(SkillContentHash.of(pack)).isEqualTo(SkillContentHash.of(pack));
        assertThat(SkillContentHash.of(pack)).hasSize(64); // SHA-256 hex
    }

    @Test
    void changesWhenThePromptChanges() {
        assertThat(SkillContentHash.of(LifecycleSkill.healthy()))
                .isNotEqualTo(SkillContentHash.of(LifecycleSkill.reworded()));
        assertThat(SkillContentHash.of(LifecycleSkill.healthy()))
                .isNotEqualTo(SkillContentHash.of(LifecycleSkill.regressed()));
    }

    @Test
    void changesWhenAReferencedTypeChanges() {
        SkillPack base = singleStepWith(
                new DynamicTypeDef("TopicBrief", "x", List.of(new PropertyDef("topic", "string", "t")),
                        List.of(), true));
        SkillPack renamed = singleStepWith(
                new DynamicTypeDef("TopicBrief", "x", List.of(new PropertyDef("topic", "integer", "t")),
                        List.of(), true));
        assertThat(SkillContentHash.of(base)).isNotEqualTo(SkillContentHash.of(renamed));
    }

    @Test
    void changesWhenAToolGroupChanges() {
        SkillPack noTools = singleStep(List.of());
        SkillPack withShell = singleStep(List.of("shell"));
        assertThat(SkillContentHash.of(noTools)).isNotEqualTo(SkillContentHash.of(withShell));
    }

    private SkillPack singleStepWith(DynamicTypeDef type) {
        ActionStepSpec step = ActionStepSpec.action("s", "d", List.of(SampleSkillPacks.USER_INPUT),
                "TopicBrief", "p");
        return new SkillPack(new SkillPackManifest("x", "1.0.0"),
                GoalStepSpec.exportedGoal("g", "d", "TopicBrief"), List.of(step), List.of(type));
    }

    private SkillPack singleStep(List<String> toolGroups) {
        ActionStepSpec step = new ActionStepSpec(ActionStepSpec.STEP_TYPE, "s", "d",
                List.of(SampleSkillPacks.USER_INPUT), SampleSkillPacks.BACKGROUND_MESSAGE,
                "p", null, null, null, null, null, null, toolGroups);
        return new SkillPack(new SkillPackManifest("x", "1.0.0"),
                GoalStepSpec.exportedGoal("g", "d", SampleSkillPacks.BACKGROUND_MESSAGE),
                List.of(step), List.of());
    }
}
