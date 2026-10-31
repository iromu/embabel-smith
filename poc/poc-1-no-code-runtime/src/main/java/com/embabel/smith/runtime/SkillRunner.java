package com.embabel.smith.runtime;

import com.embabel.agent.api.common.PlannerType;
import com.embabel.agent.core.Agent;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.AgentScope;
import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.core.ProcessOptions;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.spec.StepSpecAgentScopeBuilder;
import com.embabel.agent.spec.model.BackgroundMessage;
import com.embabel.agent.spec.yml.YmlStepSpecRepository;
import com.embabel.smith.common.io.SkillPackReader;
import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.common.validation.SkillPackValidator;
import com.embabel.smith.common.validation.ValidationResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The fixed no-code runtime core: ingest a skill-pack from YAML with zero compilation and run a
 * plan over its exported goal.
 *
 * <p>Everything executed here is <em>data</em> (the skill-pack YAML) — the only compiled code is
 * this launcher. Ingestion uses {@link YmlStepSpecRepository} + {@link StepSpecAgentScopeBuilder#deploy()};
 * planning uses a non-model planner (default GOAP) so the step sequence is reproducible and only
 * the action bodies invoke the model.
 */
public class SkillRunner {

    private final AgentPlatform platform;
    private final SkillPackReader reader = new SkillPackReader();
    private final SkillPackValidator validator = new SkillPackValidator();
    private final SkillPackTypeMapper typeMapper = new SkillPackTypeMapper();
    private final StepSpecAssembler assembler = new StepSpecAssembler();
    private final PlannerResolver plannerResolver = new PlannerResolver();

    public SkillRunner(AgentPlatform platform) {
        this.platform = platform;
    }

    /**
     * Runs the skill-pack over {@code input} and returns the exported goal's output content.
     *
     * @param packDir         the skill-pack directory
     * @param input           the user input bound to the {@code UserInput} type
     * @param plannerOverride a per-run planner name, or {@code null} to use the default
     * @return the goal output (a {@link BackgroundMessage}'s content)
     */
    public String run(Path packDir, String input, String plannerOverride) {
        SkillPack pack = loadAndValidate(packDir);

        DataDictionary types = platform.plus(
                DataDictionary.fromDomainTypes("skill-types", typeMapper.allRuntimeTypes(pack)));

        Path stepsDir = assembleStepSpecs(packDir);

        StepSpecAgentScopeBuilder builder = new StepSpecAgentScopeBuilder(
                "skill-agent",
                platform,
                new YmlStepSpecRepository(stepsDir.toString(), List.of()),
                types,
                List.of(),
                List.of());
        AgentScope scope = builder.deploy();
        Agent agent = scope.createAgent("skill-agent", "skill", "YAML-defined skill: " + pack.name());
        platform.deploy(agent);

        PlannerType planner = plannerResolver.resolve(plannerOverride, pack);
        AgentProcess process = platform.runAgentFrom(
                agent,
                ProcessOptions.DEFAULT.withPlannerType(planner),
                Map.of("userInput", new UserInput(input)));

        return extractGoalOutput(process, planner);
    }

    private SkillPack loadAndValidate(Path packDir) {
        SkillPack pack;
        try {
            pack = reader.read(packDir);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read skill-pack at " + packDir + ": " + e.getMessage(), e);
        }
        ValidationResult result = validator.validate(packDir);
        if (!result.isValid()) {
            throw new IllegalStateException("Skill-pack failed validation: " + result.issues());
        }
        return pack;
    }

    private Path assembleStepSpecs(Path packDir) {
        try {
            Path stepsDir = Files.createTempDirectory("skill-steps-");
            assembler.assemble(packDir, stepsDir);
            return stepsDir;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to assemble step specs from " + packDir + ": " + e.getMessage(), e);
        }
    }

    private String extractGoalOutput(AgentProcess process, PlannerType planner) {
        Object last = process.lastResult();
        if (last instanceof BackgroundMessage message) {
            return message.getContent();
        }
        throw new IllegalStateException(
                "No goal output produced (planner=" + planner + ", status=" + process.getStatus()
                        + "). If the input does not satisfy the skill's preconditions, the plan cannot reach the goal.");
    }
}
