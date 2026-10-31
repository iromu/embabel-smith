package com.embabel.smith.refinement;

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
import com.embabel.agent.eval.support.FullScores;
import com.embabel.agent.eval.support.Score;
import com.embabel.agent.spec.StepSpecAgentScopeBuilder;
import com.embabel.agent.spec.model.BackgroundMessage;
import com.embabel.agent.spec.yml.YmlStepSpecRepository;
import com.embabel.smith.common.io.SkillPackWriter;
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
 * The eval-guided refinement loop (POC 9): evaluate a weak skill, rewrite its prompt from the
 * failing tasks, re-evaluate, and report the before/after composite score. Endpoint-gated (the skill
 * runs and the rewrite both call the model); the scoring and re-validation halves are pure.
 */
public class RefinementRunner {

    private static final CopyOption[] COPY = {StandardCopyOption.REPLACE_EXISTING};

    /** The model endpoint for the rewrite call (env-driven, matching the rest of the POC). */
    public record Endpoint(String baseUrl, String apiKey, String model) {
    }

    /** The outcome of a refinement pass: before/after scores, the revised prompt, and what failed. */
    public record RefinementOutcome(double beforeScore, double afterScore, String revisedPrompt,
                                    List<String> failingInputs) {
        public RefinementOutcome {
            failingInputs = failingInputs == null ? List.of() : List.copyOf(failingInputs);
        }

        public boolean improved() {
            return afterScore > beforeScore;
        }
    }

    private final AgentPlatform platform;
    private final RefinementScorer scorer = new RefinementScorer();
    private final PromptRewriter rewriter = new PromptRewriter();
    private final SkillPackWriter writer = new SkillPackWriter();
    private final SkillPackValidator validator = new SkillPackValidator();

    public RefinementRunner(AgentPlatform platform) {
        this.platform = platform;
    }

    public RefinementOutcome improve(SkillPack pack, String stepName, List<SkillEvalTask> tasks, Endpoint endpoint) {
        Agent before = deploy(pack, "refine-before");
        FullScores beforeScores = evaluate(before, tasks);
        List<SkillEvalTask> failing = failingTasks(beforeScores, tasks);
        if (failing.isEmpty()) {
            return new RefinementOutcome(beforeScores.getTotalScore(), beforeScores.getTotalScore(),
                    null, List.of());
        }

        String revisedPrompt = rewriter.rewrite(endpoint.baseUrl(), endpoint.apiKey(), endpoint.model(),
                pack.step(stepName).prompt(), failing);
        PromptRewriter.RewriteResult applied = rewriter.applyRevisedPrompt(pack, stepName, revisedPrompt);
        if (!applied.valid()) {
            throw new IllegalStateException("Revised prompt failed validation: " + applied.issues());
        }

        Agent after = deploy(applied.revisedPack(), "refine-after");
        FullScores afterScores = evaluate(after, tasks);

        return new RefinementOutcome(beforeScores.getTotalScore(), afterScores.getTotalScore(),
                revisedPrompt, failing.stream().map(SkillEvalTask::input).toList());
    }

    private FullScores evaluate(Agent agent, List<SkillEvalTask> tasks) {
        List<Score> scores = tasks.stream()
                .map(task -> scorer.scoreOne(invoke(agent, task.input()), task))
                .toList();
        return scorer.composite(scores);
    }

    private String invoke(Agent agent, String input) {
        AgentProcess process = platform.runAgentFrom(
                agent,
                ProcessOptions.DEFAULT.withPlannerType(PlannerType.GOAP),
                Map.of("userInput", new UserInput(input)));
        Object last = process.lastResult();
        return last instanceof BackgroundMessage message ? message.getContent() : null;
    }

    private List<SkillEvalTask> failingTasks(FullScores scores, List<SkillEvalTask> tasks) {
        return scores.getAssertionScores().stream()
                .filter(s -> s.getScore() < 1.0)
                .map(Score::getScored)
                .filter(input -> tasks.stream().anyMatch(t -> t.input().equals(input)))
                .map(input -> tasks.stream().filter(t -> t.input().equals(input)).findFirst().orElseThrow())
                .toList();
    }

    private Agent deploy(SkillPack pack, String agentName) {
        ValidationResult result = validator.validate(pack);
        if (!result.isValid()) {
            throw new IllegalStateException("Skill-pack failed validation: " + result.issues());
        }
        try {
            DataDictionary types = platform.plus(
                    DataDictionary.fromDomainTypes(agentName + "-types", runtimeTypes(pack)));
            Path stepsDir = assembleStepSpecs(pack);
            StepSpecAgentScopeBuilder builder = new StepSpecAgentScopeBuilder(
                    agentName,
                    platform,
                    new YmlStepSpecRepository(stepsDir.toString(), List.of()),
                    types,
                    List.of(),
                    List.of());
            AgentScope scope = builder.deploy();
            Agent agent = scope.createAgent(agentName, "skill", "refinement target: " + pack.name());
            platform.deploy(agent);
            return agent;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to deploy " + pack.name() + ": " + e.getMessage(), e);
        }
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

    private Path assembleStepSpecs(SkillPack pack) throws IOException {
        Path packDir = writer.write(pack, Files.createTempDirectory("refine-pack-"));
        Path target = Files.createTempDirectory("refine-steps-");
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
