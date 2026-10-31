package com.embabel.smith.adapter;

import com.embabel.agent.api.common.PlannerType;
import com.embabel.agent.core.Agent;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.AgentScope;
import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.core.DomainType;
import com.embabel.agent.core.DynamicType;
import com.embabel.agent.core.JvmType;
import com.embabel.agent.core.PropertyDefinition;
import com.embabel.agent.core.ProcessOptions;
import com.embabel.agent.core.ToolGroupDescription;
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
import java.util.List;
import java.util.Map;

/**
 * The skill runtime adapter (POC 7): ingests a skill-pack whose action declares
 * {@code toolGroups: [shell]} and runs it, so the platform injects the real shell tool and the
 * skill executes an actual CLI command — no orchestration code. The tool group is registered as a
 * platform bean (see {@link ShellToolGroup}); the builder is told the scope wants the {@code shell}
 * group via a {@link ToolGroupDescription}.
 */
public class AdapterRunner {

    private static final CopyOption[] COPY = {StandardCopyOption.REPLACE_EXISTING};

    private final AgentPlatform platform;
    private final SkillPackReader reader = new SkillPackReader();
    private final SkillPackValidator validator = new SkillPackValidator();

    public AdapterRunner(AgentPlatform platform) {
        this.platform = platform;
    }

    /** Runs the CLI skill over {@code input} and returns the goal output (a {@link BackgroundMessage}). */
    public String run(Path packDir, String input) {
        SkillPack pack = loadAndValidate(packDir);

        DataDictionary types = platform.plus(
                DataDictionary.fromDomainTypes("adapter-types", runtimeTypes(pack)));

        Path stepsDir;
        try {
            stepsDir = assembleStepSpecs(packDir);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to assemble step specs from " + packDir + ": " + e.getMessage(), e);
        }

        StepSpecAgentScopeBuilder builder = new StepSpecAgentScopeBuilder(
                "adapter-agent",
                platform,
                new YmlStepSpecRepository(stepsDir.toString(), List.of()),
                types,
                List.of(ToolGroupDescription.create("Executes shell commands on the host", ShellToolGroup.ROLE)),
                List.of());
        AgentScope scope = builder.deploy();
        Agent agent = scope.createAgent("adapter-agent", "skill", "CLI skill: " + pack.name());
        platform.deploy(agent);

        AgentProcess process = platform.runAgentFrom(
                agent,
                ProcessOptions.DEFAULT.withPlannerType(PlannerType.GOAP),
                Map.of("userInput", new UserInput(input)));

        Object last = process.lastResult();
        if (last instanceof BackgroundMessage message) {
            return message.getContent();
        }
        throw new IllegalStateException("No goal output produced (status=" + process.getStatus() + ")");
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

    private List<DomainType> runtimeTypes(SkillPack pack) {
        List<DomainType> types = new ArrayList<>();
        types.add(new JvmType(UserInput.class));
        types.add(new JvmType(BackgroundMessage.class));
        for (DynamicTypeDef def : pack.types()) {
            List<PropertyDefinition> properties = new ArrayList<>();
            for (PropertyDef property : def.ownProperties()) {
                properties.add(new ValuePropertyDefinition(property.name(), property.type()));
            }
            types.add(new DynamicType(def.name(), def.description(), properties, List.of(), def.isCreationPermitted()));
        }
        return types;
    }

    private Path assembleStepSpecs(Path packDir) throws IOException {
        Path target = Files.createTempDirectory("adapter-steps-");
        Files.copy(packDir.resolve("goal.yml"), target.resolve("goal.yml"), COPY);
        Path stepsDir = packDir.resolve("steps");
        if (Files.isDirectory(stepsDir)) {
            try (var files = Files.list(stepsDir)) {
                for (Path step : files.filter(Files::isRegularFile).toList()) {
                    Files.copy(step, target.resolve(step.getFileName().toString()), COPY);
                }
            }
        }
        return target;
    }
}
