package com.embabel.smith.lifecycle;

import com.embabel.agent.api.common.PlannerType;
import com.embabel.agent.core.Agent;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.AgentScope;
import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.core.DomainType;
import com.embabel.agent.core.JvmType;
import com.embabel.agent.core.ProcessOptions;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.spec.StepSpecAgentScopeBuilder;
import com.embabel.agent.spec.model.BackgroundMessage;
import com.embabel.agent.spec.yml.YmlStepSpecRepository;
import com.embabel.smith.common.io.AgentSpecWriter;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.validation.AgentSpecValidator;
import com.embabel.smith.common.validation.ValidationResult;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

/**
 * The endpoint-gated half of POC 18: deploy a spec and measure its live eval pass-rate. Deploys
 * the spec as a throwaway agent (same wiring as POC 9's {@code RefinementRunner}) and scores the
 * goal output of each eval task against its deterministic acceptance check.
 */
public final class LiveEvalRunner {

    private static final CopyOption[] COPY = {StandardCopyOption.REPLACE_EXISTING};

    private final AgentPlatform platform;
    private final AgentSpecWriter writer = new AgentSpecWriter();
    private final AgentSpecValidator validator = new AgentSpecValidator();

    public LiveEvalRunner(AgentPlatform platform) {
        this.platform = platform;
    }

    /** The live eval pass-rate (0.0–1.0) of {@code pack} across {@code tasks}. */
    public double evaluate(AgentSpec pack, List<SpecEvalTask> tasks) {
        Agent agent = deploy(pack, "lifecycle-" + pack.name());
        double sum = 0.0;
        for (SpecEvalTask task : tasks) {
            String output = invoke(agent, task.input());
            sum += scoreOne(output, task);
        }
        return sum / tasks.size();
    }

    private double scoreOne(String goalOutput, SpecEvalTask task) {
        return goalOutput != null && !goalOutput.isBlank() && task.acceptance().test(goalOutput)
                ? 1.0 : 0.0;
    }

    private String invoke(Agent agent, String input) {
        AgentProcess process = platform.runAgentFrom(
                agent,
                ProcessOptions.DEFAULT.withPlannerType(PlannerType.GOAP),
                Map.of("userInput", new UserInput(input)));
        Object last = process.lastResult();
        return last instanceof BackgroundMessage message ? message.getContent() : null;
    }

    private Agent deploy(AgentSpec pack, String agentName) {
        ValidationResult result = validator.validate(pack);
        if (!result.isValid()) {
            throw new IllegalStateException("Spec-pack failed validation: " + result.issues());
        }
        try {
            DataDictionary types = platform.plus(
                    DataDictionary.fromDomainTypes(agentName + "-types",
                            List.of(new JvmType(UserInput.class), new JvmType(BackgroundMessage.class))));
            Path stepsDir = assembleStepSpecs(pack);
            StepSpecAgentScopeBuilder builder = new StepSpecAgentScopeBuilder(
                    agentName,
                    platform,
                    new YmlStepSpecRepository(stepsDir.toString(), List.of()),
                    types,
                    List.of(),
                    List.of());
            AgentScope scope = builder.deploy();
            Agent agent = scope.createAgent(agentName, "spec", "lifecycle target: " + pack.name());
            platform.deploy(agent);
            return agent;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to deploy " + pack.name() + ": " + e.getMessage(), e);
        }
    }

    private Path assembleStepSpecs(AgentSpec pack) throws IOException {
        Path packDir = writer.write(pack, Files.createTempDirectory("lifecycle-pack-"));
        Path target = Files.createTempDirectory("lifecycle-steps-");
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
