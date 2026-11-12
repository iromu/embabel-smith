package com.embabel.smith.trust;

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
import com.embabel.agent.core.ValuePropertyDefinition;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.spec.StepSpecAgentScopeBuilder;
import com.embabel.agent.spec.model.BackgroundMessage;
import com.embabel.agent.spec.yml.YmlStepSpecRepository;
import com.embabel.smith.common.io.AgentSpecReader;
import com.embabel.smith.common.model.DynamicTypeDef;
import com.embabel.smith.common.model.PropertyDef;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.validation.AgentSpecValidator;
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
 * Runs a agent spec and reports the outcome, distinguishing a normal goal achievement from a run
 * that was blocked (e.g. by a global guardrail). Reused for the endpoint-gated guardrail
 * demonstration in {@link TrustApp}.
 */
public class TrustRunner {

    private static final CopyOption[] COPY = {StandardCopyOption.REPLACE_EXISTING};

    /** The outcome of a run: whether the goal was achieved, its output, or an error/block reason. */
    public record RunOutcome(boolean achieved, String output, String error) {
    }

    private final AgentPlatform platform;
    private final AgentSpecReader reader = new AgentSpecReader();
    private final AgentSpecValidator validator = new AgentSpecValidator();

    public TrustRunner(AgentPlatform platform) {
        this.platform = platform;
    }

    public RunOutcome run(Path packDir, String input) {
        try {
            AgentProcess process = deployAndRun(packDir, input);
            Object last = process.lastResult();
            if (last instanceof BackgroundMessage message) {
                return new RunOutcome(true, message.getContent(), null);
            }
            return new RunOutcome(false, null, "goal not achieved (status=" + process.getStatus() + ")");
        } catch (Exception e) {
            String reason = e.getMessage() == null ? e.toString() : e.getMessage();
            if (isGuardrailBlock(e)) {
                return new RunOutcome(false, null, "blocked by guardrail: " + reason);
            }
            return new RunOutcome(false, null, "run failed: " + reason);
        }
    }

    private AgentProcess deployAndRun(Path packDir, String input) throws IOException {
        AgentSpec pack = reader.read(packDir);
        ValidationResult result = validator.validate(packDir);
        if (!result.isValid()) {
            throw new IllegalStateException("Spec-pack failed validation: " + result.issues());
        }

        DataDictionary types = platform.plus(
                DataDictionary.fromDomainTypes("trust-types", runtimeTypes(pack)));

        Path stepsDir = Files.createTempDirectory("trust-steps-");
        Files.copy(packDir.resolve("goal.yml"), stepsDir.resolve("goal.yml"), COPY);
        Path steps = packDir.resolve("steps");
        if (Files.isDirectory(steps)) {
            try (var files = Files.list(steps)) {
                for (Path step : files.filter(Files::isRegularFile).toList()) {
                    Files.copy(step, stepsDir.resolve(step.getFileName().toString()), COPY);
                }
            }
        }

        StepSpecAgentScopeBuilder builder = new StepSpecAgentScopeBuilder(
                "trust-agent",
                platform,
                new YmlStepSpecRepository(stepsDir.toString(), List.of()),
                types,
                List.of(),
                List.of());
        AgentScope scope = builder.deploy();
        Agent agent = scope.createAgent("trust-agent", "spec", "trust-tier spec: " + pack.name());
        platform.deploy(agent);

        return platform.runAgentFrom(
                agent,
                ProcessOptions.DEFAULT.withPlannerType(PlannerType.GOAP),
                Map.of("userInput", new UserInput(input)));
    }

    private List<DomainType> runtimeTypes(AgentSpec pack) {
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

    /** Walks the cause chain for a guardrail violation (Embabel may wrap it). */
    private boolean isGuardrailBlock(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
            String name = c.getClass().getSimpleName();
            if (name.contains("GuardRailViolation") || name.contains("GuardrailViolation")) {
                return true;
            }
            String msg = c.getMessage();
            if (msg != null && msg.contains("guardrail")) {
                return true;
            }
        }
        return false;
    }
}
