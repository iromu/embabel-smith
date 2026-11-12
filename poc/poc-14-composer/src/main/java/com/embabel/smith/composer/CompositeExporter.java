package com.embabel.smith.composer;

import com.embabel.smith.common.io.AgentSpecWriter;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.DynamicTypeDef;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.model.AgentSpecManifest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exports a valid {@link CompositionCanvas} as a <em>single</em> new agent spec — the "composite
 * spec" of doc 03 §2.2: an exported goal whose action chain <em>is</em> the composed plan.
 *
 * <p>The composite pack is the union of the component specs' action steps and intermediate types,
 * plus one new exported goal whose output type is the exit spec's output type. The component
 * specs' own goals are dropped — only their typed actions matter, and the non-LLM GOAP planner
 * re-derives the chain from those actions' pre/postconditions. The result is a plain, loadable
 * agent spec: the no-code runtime ingests it exactly as it ingests a hand-authored one.
 */
public final class CompositeExporter {

    private final AgentSpecWriter writer = new AgentSpecWriter();

    /**
     * Builds the composite pack from the canvas's chain.
     *
     * @param compositeName the name for the new composite agent spec
     */
    public AgentSpec buildComposite(String compositeName, CompositionCanvas canvas) {
        List<SpecNode> chain = canvas.chain();

        Map<String, ActionStepSpec> steps = new LinkedHashMap<>();
        Map<String, DynamicTypeDef> types = new LinkedHashMap<>();
        for (SpecNode node : chain) {
            node.pack().steps().forEach(step -> steps.putIfAbsent(step.name(), step));
            node.pack().types().forEach(type -> types.putIfAbsent(type.name(), type));
        }

        String outputType = chain.get(chain.size() - 1).outputType();
        String goalName = compositeName + "Goal";
        String description = "Composite spec chaining: " + canvas.describeChain();
        GoalStepSpec goal = GoalStepSpec.exportedGoal(goalName, description, outputType);
        AgentSpecManifest manifest = new AgentSpecManifest(compositeName, "1.0.0", "GOAP", null);

        return new AgentSpec(manifest, goal, new ArrayList<>(steps.values()),
                new ArrayList<>(types.values()));
    }

    /**
     * Builds the composite pack and writes it under {@code outDir}/{@code compositeName}.
     *
     * @return the directory the composite pack was written to
     */
    public Path export(String compositeName, CompositionCanvas canvas, Path outDir) throws IOException {
        AgentSpec composite = buildComposite(compositeName, canvas);
        return writer.write(composite, outDir.resolve(compositeName));
    }
}
