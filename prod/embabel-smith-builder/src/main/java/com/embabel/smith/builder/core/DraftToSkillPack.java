package com.embabel.smith.builder.core;

import com.embabel.agent.core.DomainType;
import com.embabel.agent.core.DynamicType;
import com.embabel.agent.core.NamedPropertyDefinition;
import com.embabel.agent.spec.compile.AgentCompilationResult;
import com.embabel.agent.spec.model.GoalSpec;
import com.embabel.agent.spec.model.PromptedActionSpec;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.DynamicTypeDef;
import com.embabel.smith.common.model.GoalStepSpec;
import com.embabel.smith.common.model.PropertyDef;
import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.common.model.SkillPackManifest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The seam between the Embabel NL-compiler output ({@link AgentCompilationResult}) and the shared
 * skill-pack format ({@link SkillPack}). The builder's downstream steps (validate, write) operate
 * on the format's own types, so the Embabel-specific types are confined to this conversion and to
 * the endpoint-gated compile step.
 */
public class DraftToSkillPack {

    public SkillPack toSkillPack(AgentCompilationResult result, String agentName) {
        List<ActionStepSpec> steps = result.getActions().stream()
                .map(this::toActionStep)
                .toList();
        GoalStepSpec goal = result.getGoal() == null ? null : toGoal(result.getGoal());
        List<DynamicTypeDef> types = result.getIntermediateTypes().stream()
                .map(this::toType)
                .toList();
        SkillPackManifest manifest = new SkillPackManifest(agentName, "1.0.0");
        return normalizeGoalOutput(new SkillPack(manifest, goal, steps, types));
    }

    /**
     * A skill-pack's goal output is the type the runtime returns for a run, so it must be a concrete,
     * returnable type. The NL compiler sometimes emits a degenerate goal output — {@code void} or a
     * scalar (when the skill "posts a background message" as a side effect), or the skill's own
     * <em>input</em> type (a no-op goal that is trivially satisfied without doing any work). A
     * {@code void} goal never registers in the GOAP world state (the plan STUCKs); a no-op goal
     * "succeeds" instantly with no output to return. When the goal output is such a type, remap the
     * goal <em>and</em> the action that produces it to the canonical {@code BackgroundMessage}, so the
     * emitted pack's final output is returnable and the chain connects; drop the now-unused degenerate
     * type declaration.
     */
    private SkillPack normalizeGoalOutput(SkillPack pack) {
        GoalStepSpec goal = pack.goal();
        String goalOutput = goal == null ? null : goal.outputTypeName();
        if (goalOutput == null || !isNonReturnable(goalOutput)) {
            return pack; // goal output is a concrete, returnable type — leave the draft as-is
        }
        List<ActionStepSpec> steps = pack.steps().stream()
                .map(s -> goalOutput.equals(s.outputTypeName()) ? withOutput(s, BACKGROUND_MESSAGE) : s)
                .toList();
        GoalStepSpec newGoal = GoalStepSpec.exportedGoal(goal.name(), goal.description(), BACKGROUND_MESSAGE);
        Set<String> used = new HashSet<>();
        used.add(BACKGROUND_MESSAGE);
        for (ActionStepSpec s : steps) {
            used.addAll(s.inputTypeNames());
            if (s.outputTypeName() != null) {
                used.add(s.outputTypeName());
            }
        }
        List<DynamicTypeDef> types = pack.types().stream().filter(t -> used.contains(t.name())).toList();
        return new SkillPack(pack.manifest(), newGoal, steps, types);
    }

    /** A goal output the runtime cannot return: a scalar/void type, or the skill's own input type. */
    private boolean isNonReturnable(String type) {
        return NON_RETURNABLE_OUTPUTS.contains(type)
                || USER_INPUT.equals(type) || SYSTEM_INPUT.equals(type);
    }

    private ActionStepSpec withOutput(ActionStepSpec s, String output) {
        return new ActionStepSpec(s.stepType(), s.name(), s.description(), s.inputTypeNames(),
                output, s.prompt(), s.pre(), s.post(), s.cost(), s.value(),
                s.canRerun(), s.nullable(), s.toolGroups());
    }

    /** Goal output types that are not concrete, returnable types (the runtime can't return them). */
    private static final Set<String> NON_RETURNABLE_OUTPUTS = Set.of(
            "void", "string", "integer", "long", "number", "double", "float", "boolean", "char",
            "java.lang.Void", "java.lang.String", "java.lang.Integer", "java.lang.Long",
            "java.lang.Double", "java.lang.Float", "java.lang.Boolean", "java.lang.Character");

    /** The canonical goal-output type a skill returns to the user. */
    private static final String BACKGROUND_MESSAGE = "com.embabel.agent.spec.model.BackgroundMessage";

    /** The framework's entry types — a goal that "outputs" one of these is a no-op (it echoes its input). */
    private static final String USER_INPUT = "com.embabel.agent.domain.io.UserInput";
    private static final String SYSTEM_INPUT = "com.embabel.agent.domain.io.SystemInput";

    private ActionStepSpec toActionStep(PromptedActionSpec spec) {
        return new ActionStepSpec(
                ActionStepSpec.STEP_TYPE,
                spec.getName(),
                spec.getDescription(),
                new ArrayList<>(spec.getInputTypeNames()),
                spec.getOutputTypeName(),
                spec.getPrompt(),
                spec.getPre(),
                spec.getPost(),
                spec.getCost(),
                spec.getValue(),
                spec.getCanRerun(),
                spec.getNullable(),
                spec.getToolGroups());
    }

    private GoalStepSpec toGoal(GoalSpec spec) {
        // A skill-pack exposes exactly one callable skill, and that skill IS its goal — so the goal
        // is exported by definition, independent of the NL compiler's export flag (a general-purpose
        // default that is false for a freshly compiled agent goal). Forcing it here keeps the builder's
        // output conforming to the format invariant; downstream validation still guards everything else.
        return GoalStepSpec.exportedGoal(
                spec.getName(),
                spec.getDescription(),
                spec.getOutputTypeName());
    }

    private DynamicTypeDef toType(DynamicType type) {
        List<PropertyDef> properties = type.getOwnProperties().stream()
                .map(p -> new PropertyDef(
                        p.getName(),
                        p instanceof NamedPropertyDefinition named ? named.getType() : null,
                        p.getDescription()))
                .toList();
        List<String> parents = type.getParents().stream()
                .map(DomainType::getName)
                .toList();
        return new DynamicTypeDef(type.getName(), type.getDescription(), properties, parents, true);
    }
}
