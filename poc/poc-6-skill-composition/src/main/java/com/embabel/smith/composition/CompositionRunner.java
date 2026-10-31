package com.embabel.smith.composition;

import com.embabel.agent.api.common.PlannerType;
import com.embabel.agent.core.Agent;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.AgentScope;
import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.core.DomainType;
import com.embabel.agent.core.DynamicType;
import com.embabel.agent.core.Goal;
import com.embabel.agent.core.JvmType;
import com.embabel.agent.core.PropertyDefinition;
import com.embabel.agent.core.ProcessOptions;
import com.embabel.agent.core.ValuePropertyDefinition;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.spec.StepSpecAgentScopeBuilder;
import com.embabel.agent.spec.model.BackgroundMessage;
import com.embabel.agent.spec.yml.YmlStepSpecRepository;
import com.embabel.smith.common.io.SkillPackReader;
import com.embabel.smith.common.model.DynamicTypeDef;
import com.embabel.smith.common.model.PropertyDef;
import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.common.validation.SkillPackValidator;
import com.embabel.smith.common.validation.ValidationResult;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The skill-composition runtime (POC 6): loads several independent skill-packs into one
 * {@link AgentPlatform} and runs a plan over the final goal, so the non-LLM GOAP planner chains an
 * action from each pack through their shared intermediate type.
 *
 * <p>This is the "skills, not agents" composition claim: the packs are authored and validated
 * independently, and only their typed pre/postconditions (the shared intermediate type) are needed
 * for the planner to route across them — no orchestration code.
 */
public class CompositionRunner {

    private static final CopyOption[] COPY = {StandardCopyOption.REPLACE_EXISTING};

    private final AgentPlatform platform;
    private final SkillPackReader reader = new SkillPackReader();
    private final SkillPackValidator validator = new SkillPackValidator();

    public CompositionRunner(AgentPlatform platform) {
        this.platform = platform;
    }

    /**
     * Composes the given skill-packs and runs a plan to {@code targetGoalName} over {@code input}.
     *
     * @return the final goal's output (a {@link BackgroundMessage}'s content)
     */
    public String compose(List<Path> packDirs, String targetGoalName, String input, String plannerOverride) {
        List<SkillPack> packs = loadAndValidateAll(packDirs);

        DataDictionary types = platform.plus(
                DataDictionary.fromDomainTypes("compose-types", combinedTypes(packs)));
        Path stepsDir;
        try {
            stepsDir = assembleCombined(packDirs);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to assemble composed step specs: " + e.getMessage(), e);
        }

        StepSpecAgentScopeBuilder builder = new StepSpecAgentScopeBuilder(
                "compose-agent",
                platform,
                new YmlStepSpecRepository(stepsDir.toString(), List.of()),
                types,
                List.of(),
                List.of());
        AgentScope scope = builder.deploy();
        Agent agent = scope.createAgent("compose-agent", "skill", "composed skills");

        Agent singleGoal = agent.withSingleGoal(findGoal(agent, targetGoalName));
        platform.deploy(singleGoal);

        AgentProcess process = platform.runAgentFrom(
                singleGoal,
                ProcessOptions.DEFAULT.withPlannerType(resolvePlanner(plannerOverride)),
                Map.of("userInput", new UserInput(input)));

        return extractGoalOutput(process);
    }

    /**
     * The domain types the composed run needs: the built-in I/O types plus every pack's intermediate
     * types, deduplicated by name — the shared type is the composition's glue.
     */
    public List<DomainType> combinedTypes(List<SkillPack> packs) {
        List<DomainType> types = new ArrayList<>();
        types.add(new JvmType(UserInput.class));
        types.add(new JvmType(BackgroundMessage.class));
        Set<String> seen = new HashSet<>();
        for (SkillPack pack : packs) {
            for (DynamicTypeDef def : pack.types()) {
                if (seen.add(def.name())) {
                    types.add(toDynamicType(def));
                }
            }
        }
        return types;
    }

    /**
     * Merges each pack's {@code goal.yml} (renamed to avoid cross-pack collision) and every
     * {@code steps/*.yml} into one flat directory that {@link YmlStepSpecRepository} reads, so all
     * goals and actions are ingested together and thus planned together. Source packs are untouched.
     */
    public Path assembleCombined(List<Path> packDirs) throws IOException {
        Path target = Files.createTempDirectory("compose-steps-");
        int i = 0;
        for (Path packDir : packDirs) {
            Files.copy(packDir.resolve("goal.yml"), target.resolve("goal-" + i + ".yml"), COPY);
            Path stepsDir = packDir.resolve("steps");
            if (Files.isDirectory(stepsDir)) {
                try (var files = Files.list(stepsDir)) {
                    for (Path step : files.filter(Files::isRegularFile).toList()) {
                        Files.copy(step, target.resolve(step.getFileName().toString()), COPY);
                    }
                }
            }
            i++;
        }
        return target;
    }

    private List<SkillPack> loadAndValidateAll(List<Path> packDirs) {
        List<SkillPack> packs = new ArrayList<>();
        for (Path dir : packDirs) {
            SkillPack pack;
            try {
                pack = reader.read(dir);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read skill-pack at " + dir + ": " + e.getMessage(), e);
            }
            ValidationResult result = validator.validate(dir);
            if (!result.isValid()) {
                throw new IllegalStateException("Skill-pack " + dir + " failed validation: " + result.issues());
            }
            packs.add(pack);
        }
        return packs;
    }

    private Goal findGoal(Agent agent, String name) {
        return agent.getGoals().stream()
                .filter(goal -> name.equals(goal.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No goal named '" + name + "' in the composed agent. Goals: "
                                + agent.getGoals().stream().map(Goal::getName).toList()));
    }

    private PlannerType resolvePlanner(String override) {
        if (override != null && !override.isBlank()) {
            return PlannerType.valueOf(override.trim().toUpperCase());
        }
        return PlannerType.GOAP; // composition needs the deterministic GOAP planner
    }

    private String extractGoalOutput(AgentProcess process) {
        Object last = process.lastResult();
        if (last instanceof BackgroundMessage message) {
            return message.getContent();
        }
        throw new IllegalStateException(
                "No goal output produced (status=" + process.getStatus()
                        + "). The composed chain must end in a BackgroundMessage goal.");
    }

    private DynamicType toDynamicType(DynamicTypeDef def) {
        List<PropertyDefinition> properties = new ArrayList<>();
        for (PropertyDef property : def.ownProperties()) {
            properties.add(new ValuePropertyDefinition(property.name(), property.type()));
        }
        return new DynamicType(def.name(), def.description(), properties, List.of(), def.isCreationPermitted());
    }
}
