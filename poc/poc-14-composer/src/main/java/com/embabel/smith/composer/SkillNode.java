package com.embabel.smith.composer;

import com.embabel.smith.common.model.SkillPack;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A node on the composition canvas: one skill (an exported goal + its action steps).
 *
 * <p>A skill's <em>external</em> surface is what the canvas reasons about. Its <b>output</b> is the
 * exported goal's output type. Its <b>external inputs</b> are the action input types that are not
 * produced by any other action in the same skill — i.e. the type(s) the skill needs from the
 * outside (for a single-action skill, just that action's input type). The drag-and-drop "snap" is
 * exactly "skill A's output type is one of skill B's external inputs".
 */
public final class SkillNode {

    private final String id;
    private final SkillPack pack;

    public SkillNode(SkillPack pack) {
        this.pack = pack;
        this.id = pack.name();
    }

    public String id() {
        return id;
    }

    public SkillPack pack() {
        return pack;
    }

    /** The output type of the skill's exported goal. */
    public String outputType() {
        return pack.goal().outputTypeName();
    }

    /**
     * The types the skill consumes from outside itself: every action input type minus every type an
     * action in the skill produces. Normally a single type (the skill's entry type).
     */
    public Set<String> externalInputTypes() {
        Set<String> produced = new LinkedHashSet<>();
        Set<String> inputs = new LinkedHashSet<>();
        pack.steps().forEach(step -> {
            if (step.outputTypeName() != null) {
                produced.add(step.outputTypeName());
            }
            inputs.addAll(step.inputTypeNames());
        });
        inputs.removeAll(produced);
        return inputs;
    }

    @Override
    public String toString() {
        return id + " (in: " + externalInputTypes() + " -> out: " + outputType() + ")";
    }
}
