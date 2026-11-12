package com.embabel.smith.composer;

import com.embabel.smith.common.model.AgentSpec;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A node on the composition canvas: one spec (an exported goal + its action steps).
 *
 * <p>A spec's <em>external</em> surface is what the canvas reasons about. Its <b>output</b> is the
 * exported goal's output type. Its <b>external inputs</b> are the action input types that are not
 * produced by any other action in the same spec — i.e. the type(s) the spec needs from the
 * outside (for a single-action spec, just that action's input type). The drag-and-drop "snap" is
 * exactly "spec A's output type is one of spec B's external inputs".
 */
public final class SpecNode {

    private final String id;
    private final AgentSpec pack;

    public SpecNode(AgentSpec pack) {
        this.pack = pack;
        this.id = pack.name();
    }

    public String id() {
        return id;
    }

    public AgentSpec pack() {
        return pack;
    }

    /** The output type of the spec's exported goal. */
    public String outputType() {
        return pack.goal().outputTypeName();
    }

    /**
     * The types the spec consumes from outside itself: every action input type minus every type an
     * action in the spec produces. Normally a single type (the spec's entry type).
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
