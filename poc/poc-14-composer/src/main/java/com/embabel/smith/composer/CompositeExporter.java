package com.embabel.smith.composer;

import com.embabel.smith.common.io.SkillPackWriter;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.DynamicTypeDef;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.common.model.SkillPackManifest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exports a valid {@link CompositionCanvas} as a <em>single</em> new skill-pack — the "composite
 * skill" of doc 03 §2.2: an exported goal whose action chain <em>is</em> the composed plan.
 *
 * <p>The composite pack is the union of the component skills' action steps and intermediate types,
 * plus one new exported goal whose output type is the exit skill's output type. The component
 * skills' own goals are dropped — only their typed actions matter, and the non-LLM GOAP planner
 * re-derives the chain from those actions' pre/postconditions. The result is a plain, loadable
 * skill-pack: the no-code runtime ingests it exactly as it ingests a hand-authored one.
 */
public final class CompositeExporter {

    private final SkillPackWriter writer = new SkillPackWriter();

    /**
     * Builds the composite pack from the canvas's chain.
     *
     * @param compositeName the name for the new composite skill-pack
     */
    public SkillPack buildComposite(String compositeName, CompositionCanvas canvas) {
        List<SkillNode> chain = canvas.chain();

        Map<String, ActionStepSpec> steps = new LinkedHashMap<>();
        Map<String, DynamicTypeDef> types = new LinkedHashMap<>();
        for (SkillNode node : chain) {
            node.pack().steps().forEach(step -> steps.putIfAbsent(step.name(), step));
            node.pack().types().forEach(type -> types.putIfAbsent(type.name(), type));
        }

        String outputType = chain.get(chain.size() - 1).outputType();
        String goalName = compositeName + "Goal";
        String description = "Composite skill chaining: " + canvas.describeChain();
        GoalStepSpec goal = GoalStepSpec.exportedGoal(goalName, description, outputType);
        SkillPackManifest manifest = new SkillPackManifest(compositeName, "1.0.0", "GOAP", null);

        return new SkillPack(manifest, goal, new ArrayList<>(steps.values()),
                new ArrayList<>(types.values()));
    }

    /**
     * Builds the composite pack and writes it under {@code outDir}/{@code compositeName}.
     *
     * @return the directory the composite pack was written to
     */
    public Path export(String compositeName, CompositionCanvas canvas, Path outDir) throws IOException {
        SkillPack composite = buildComposite(compositeName, canvas);
        return writer.write(composite, outDir.resolve(compositeName));
    }
}
