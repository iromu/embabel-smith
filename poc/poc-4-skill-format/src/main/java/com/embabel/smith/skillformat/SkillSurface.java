package com.embabel.smith.skillformat;

import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.model.SkillPack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The exported-goal-as-skill primitive (POC 4).
 *
 * <p>An Embabel "skill" is an <em>exported goal</em>. This resolves, for a skill-pack, which goal
 * is the callable skill and traces the action chain that the planner will walk from the entry type
 * to the goal's output type. A goal declared {@code export: false} is <em>not</em> a callable
 * skill, so it is not exposed.
 */
public class SkillSurface {

    /** Whether the pack exposes a callable skill (an exported goal). */
    public boolean isCallableSkill(SkillPack pack) {
        GoalStepSpec goal = pack.goal();
        return goal != null && goal.isExported();
    }

    /**
     * The pack's callable skill (its exported goal), or {@code null} when the goal is not
     * exported — in which case no callable skill is exposed.
     */
    public GoalStepSpec callableSkill(SkillPack pack) {
        GoalStepSpec goal = pack.goal();
        return (goal != null && goal.isExported()) ? goal : null;
    }

    /**
     * The ordered action chain the planner walks from the entry type to the goal's output type.
     *
     * <p>Traces backwards from the goal's output type through the steps that produce each needed
     * type, until it reaches a type no step produces (the entry, e.g. {@code UserInput}). Assumes a
     * single-input chain (the common case for POC skill-packs).
     */
    public List<ActionStepSpec> actionChain(SkillPack pack) {
        List<ActionStepSpec> chain = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String needed = pack.goalOutputType();
        while (needed != null && !seen.contains(needed)) {
            seen.add(needed);
            ActionStepSpec producer = findProducer(pack, needed);
            if (producer == null) {
                break; // entry type reached: no step produces it
            }
            chain.add(0, producer);
            needed = producer.inputTypeNames().isEmpty() ? null : producer.inputTypeNames().get(0);
        }
        return chain;
    }

    private ActionStepSpec findProducer(SkillPack pack, String typeName) {
        return pack.steps().stream()
                .filter(s -> typeName.equals(s.outputTypeName()))
                .findFirst()
                .orElse(null);
    }
}
