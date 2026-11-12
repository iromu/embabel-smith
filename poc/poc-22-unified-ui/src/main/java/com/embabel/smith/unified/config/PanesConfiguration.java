package com.embabel.smith.unified.config;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.spec.compile.AgentCompilationResult;
import com.embabel.agent.spec.model.PromptedActionSpec;
import com.embabel.common.ai.model.LlmOptions;
import com.embabel.smith.common.config.ModelEndpointConfig;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.DraftQuality;
import com.embabel.smith.common.model.AgentSpec;
import com.embabel.smith.composer.SpecNode;
import com.embabel.smith.lifecycle.LiveEvalRunner;
import com.embabel.smith.nlcompile.NlCompileRunner;
import com.embabel.smith.refinement.RefinementRunner;
import com.embabel.smith.retrieval.embedding.Embedder;
import com.embabel.smith.retrieval.embedding.MiniLmEmbedder;
import com.embabel.smith.retrieval.embedding.RealEmbeddingScorer;
import com.embabel.smith.retrieval.scale.LexicalScorer;
import com.embabel.smith.runtime.SpecRunner;
import com.embabel.smith.unified.AuthorController;
import com.embabel.smith.unified.EvalController;
import com.embabel.smith.unified.LifecycleController;
import com.embabel.smith.unified.RunController;
import com.embabel.smith.unified.ScorerBinding;
import com.embabel.smith.unified.SpecCatalog;
import com.embabel.smith.unified.dto.ActionInfo;
import com.embabel.smith.unified.dto.AuthorDraft;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Wires the env-driven model endpoint and the endpoint-gated pane collaborators — the model-dependent
 * calls each gated controller makes behind its seam. The offline panes need no wiring here: their
 * controllers delegate straight to the pure POC model. Keeping the model calls here (rather than in
 * the controllers) is what makes the endpoint-present path testable with a stub.
 */
@Configuration
public class PanesConfiguration {

    @Bean
    public ModelEndpointConfig modelEndpointConfig(
            @Value("${OPENAI_CUSTOM_BASE_URL:}") String baseUrl,
            @Value("${OPENAI_CUSTOM_API_KEY:}") String apiKey,
            @Value("${OPENAI_CUSTOM_MODELS:}") String models,
            @Value("${EMBABEL_MODELS_DEFAULT_LLM:}") String defaultLlm) {
        return new ModelEndpointConfig(baseUrl, apiKey, models, defaultLlm);
    }

    /**
     * The active retrieval scorer (the shared {@code SpecScorer} seam). Default: the offline lexical
     * scorer. Opt-in: {@code builder.scorer=embedding} wires POC 20's real ONNX embedding scorer
     * without touching the panes — the Retrieve and Scale responses carry the active scorer name.
     */
    @Bean
    @ConditionalOnProperty(name = "builder.scorer", havingValue = "lexical", matchIfMissing = true)
    public ScorerBinding lexicalScorerBinding() {
        return new ScorerBinding(new LexicalScorer(), "lexical");
    }

    @Bean
    @ConditionalOnProperty(name = "builder.scorer", havingValue = "embedding")
    public MiniLmEmbedder miniLmEmbedder(
            @Value("${builder.embedding-model-dir:poc/poc-20-semantic-embedding/models/all-MiniLM-L6-v2}")
            String modelDir) throws IOException {
        return MiniLmEmbedder.load(Path.of(modelDir));
    }

    @Bean
    @ConditionalOnProperty(name = "builder.scorer", havingValue = "embedding")
    public ScorerBinding embeddingScorerBinding(Embedder embedder) {
        return new ScorerBinding(new RealEmbeddingScorer(embedder), "embedding (MiniLM)");
    }

    @Bean
    public RunController.RunFn runFn(AgentPlatform platform, SpecCatalog catalog) {
        return (specName, input) -> new SpecRunner(platform).run(catalog.dir(specName), input, null);
    }

    @Bean
    public AuthorController.CompileFn compileFn(Ai ai, AgentPlatform platform) {
        return (agentName, description) -> {
            AgentCompilationResult result =
                    new NlCompileRunner(ai, platform, LlmOptions.withDefaultLlm())
                            .compile(agentName, description);
            return toDraft(result);
        };
    }

    @Bean
    public EvalController.EvalFn evalFn(AgentPlatform platform, SpecCatalog catalog,
                                        ModelEndpointConfig endpoint) {
        return (specName, tasks) -> {
            SpecNode node = catalog.get(specName);
            AgentSpec pack = node.pack();
            RefinementRunner runner = new RefinementRunner(platform);
            RefinementRunner.Endpoint ep = new RefinementRunner.Endpoint(
                    endpoint.baseUrl(), endpoint.apiKey(), endpoint.defaultLlm());
            return runner.improve(pack, pickStep(pack), tasks, ep);
        };
    }

    @Bean
    public LifecycleController.LiveEvalFn liveEvalFn(AgentPlatform platform, SpecCatalog catalog) {
        return (specName, tasks) ->
                new LiveEvalRunner(platform).evaluate(catalog.get(specName).pack(), tasks);
    }

    private static AuthorDraft toDraft(AgentCompilationResult result) {
        String goalOutput = result.getGoal() == null ? null : result.getGoal().getOutputTypeName();
        List<String> actionOutputs = result.getActions().stream()
                .map(PromptedActionSpec::getOutputTypeName)
                .toList();
        DraftQuality.Assessment quality = DraftQuality.assess(goalOutput, actionOutputs);
        List<ActionInfo> actions = result.getActions().stream()
                .map(a -> new ActionInfo(a.getName(), List.copyOf(a.getInputTypeNames()),
                        a.getOutputTypeName()))
                .toList();
        List<String> issues = result.getErrors().stream()
                .map(e -> e.getMessage() + " [" + e.getSource() + "]")
                .toList();
        return new AuthorDraft(
                result.getGoal() == null ? null : result.getGoal().getName(),
                goalOutput,
                result.getGoal() != null && result.getGoal().getExport(),
                actions,
                quality.verdict().name(),
                quality.isDegenerate(),
                quality.reason(),
                issues);
    }

    private static String pickStep(AgentSpec pack) {
        return pack.steps().stream()
                .filter(s -> s.prompt() != null && !s.prompt().isBlank())
                .map(ActionStepSpec::name)
                .findFirst()
                .orElseGet(() -> pack.steps().isEmpty() ? null : pack.steps().get(0).name());
    }
}
