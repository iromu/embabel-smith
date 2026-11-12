package com.embabel.smith.eval;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.Agent;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentScope;
import com.embabel.agent.core.DataDictionary;
import com.embabel.agent.eval.support.FullScores;
import com.embabel.agent.spec.StepSpecAgentScopeBuilder;
import com.embabel.agent.spec.model.BackgroundMessage;
import com.embabel.agent.spec.yml.YmlStepSpecRepository;
import com.embabel.smith.common.config.ModelEndpointConfig;
import com.embabel.smith.common.io.AgentSpecReader;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.common.validation.AgentSpecValidator;
import com.embabel.smith.common.validation.ValidationResult;
import com.embabel.smith.runtime.AgentSpecTypeMapper;
import com.embabel.smith.runtime.StepSpecAssembler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * POC 5 launcher — evaluates a goal-type spec against a set of tasks and prints a composite score.
 *
 * <p>Boots an Embabel {@link AgentPlatform}, deploys the target agent spec (reusing the no-code
 * runtime's type-mapping / step-spec assembly), invokes it via {@link AgentInvocation} (the
 * non-chat path), and scores the goal outputs. The model endpoint is fully env-driven
 * ({@link ModelEndpointConfig}, design D4).
 *
 * <p>Run (with a live OpenAI-compatible endpoint):
 * <pre>
 *   export OPENAI_CUSTOM_BASE_URL=http://localhost:4000
 *   export OPENAI_CUSTOM_API_KEY=sk-...
 *   export OPENAI_CUSTOM_MODELS=my-model
 *   export EMBABEL_MODELS_DEFAULT_LLM=my-model
 *   mvn -pl poc/poc-5-eval spring-boot:run -Dbuilder.pack-dir=agent-specs/explainer
 * </pre>
 */
@SpringBootApplication
public class EvalApp implements CommandLineRunner {

    private final AgentPlatform platform;
    private final ModelEndpointConfig endpoint;
    private final Path packDir;
    private final int runs;

    public EvalApp(AgentPlatform platform,
                   @Value("${OPENAI_CUSTOM_BASE_URL:}") String baseUrl,
                   @Value("${OPENAI_CUSTOM_API_KEY:}") String apiKey,
                   @Value("${OPENAI_CUSTOM_MODELS:}") String models,
                   @Value("${EMBABEL_MODELS_DEFAULT_LLM:}") String defaultLlm,
                   @Value("${builder.pack-dir:agent-specs/explainer}") Path packDir,
                   @Value("${BUILDER_EVAL_RUNS:5}") int runs) {
        this.platform = platform;
        this.endpoint = new ModelEndpointConfig(baseUrl, apiKey, models, defaultLlm);
        this.packDir = packDir;
        this.runs = Math.max(1, runs);
    }

    public static void main(String[] args) {
        SpringApplication.run(EvalApp.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        String configError = endpoint.configError();
        if (configError != null) {
            throw new IllegalStateException(configError);
        }

        AgentSpec pack = loadAndValidate(packDir);
        AgentScope scope = buildScope(pack);
        Agent agent = scope.createAgent("eval-target", "eval-spec", "Eval target: " + pack.name());
        platform.deploy(agent);

        AgentInvocation<BackgroundMessage> invocation = AgentInvocation.create(platform, BackgroundMessage.class);
        SpecInvoker invoker = new AgentInvocationSpecInvoker(invocation);

        List<SpecEvalTask> tasks = List.of(
                new SpecEvalTask("Explain how a coffee machine works",
                        "a plain-language explainer about coffee machines",
                        out -> out.toLowerCase().contains("coffee")),
                new SpecEvalTask("Explain what a black hole is",
                        "an explanation of black holes",
                        out -> out.toLowerCase().contains("black hole")));

        GoalSpecEvaluator evaluator = new GoalSpecEvaluator();
        List<Double> totals = new ArrayList<>();
        FullScores scores = null;
        for (int i = 0; i < runs; i++) {
            scores = evaluator.evaluate(invoker, tasks);
            totals.add(scores.getTotalScore());
        }

        System.out.println("=== Eval composite (" + pack.name() + ") ===");
        System.out.println("total score: " + scores.getTotalScore());
        System.out.println("failures:    " + scores.getFailureCount());
        scores.getAssertionScores()
                .forEach(s -> System.out.println("  [" + s.getScore() + "] " + s.getScored()));

        if (runs > 1) {
            ScoreStability stability = ScoreStability.of(totals);
            System.out.println();
            System.out.println("=== Eval-score stability (" + runs + " runs, same spec + tasks) ===");
            System.out.println("per-run totals: " + totals);
            System.out.printf("mean=%.3f  min=%.3f  max=%.3f  range=%.3f  stdev=%.3f%n",
                    stability.mean(), stability.min(), stability.max(), stability.range(), stability.stdev());
            System.out.println("verdict: " + (stability.stable()
                    ? "STABLE — the eval is a repeatable signal for this spec"
                    : "NOISY — the score varies run-to-run; read it as a distribution, not a point"));
        }
    }

    private AgentSpec loadAndValidate(Path dir) throws Exception {
        AgentSpec pack = new AgentSpecReader().read(dir);
        ValidationResult result = new AgentSpecValidator().validate(dir);
        if (!result.isValid()) {
            throw new IllegalStateException("Spec-pack failed validation: " + result.issues());
        }
        return pack;
    }

    private AgentScope buildScope(AgentSpec pack) throws Exception {
        DataDictionary types = platform.plus(
                DataDictionary.fromDomainTypes("spec-types", new AgentSpecTypeMapper().allRuntimeTypes(pack)));
        Path stepsDir = Files.createTempDirectory("eval-steps-");
        new StepSpecAssembler().assemble(packDir, stepsDir);
        StepSpecAgentScopeBuilder builder = new StepSpecAgentScopeBuilder(
                "eval-target",
                platform,
                new YmlStepSpecRepository(stepsDir.toString(), List.of()),
                types,
                List.of(),
                List.of());
        return builder.deploy();
    }
}
