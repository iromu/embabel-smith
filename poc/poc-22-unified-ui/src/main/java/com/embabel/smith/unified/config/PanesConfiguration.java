package com.embabel.smith.unified.config;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.spec.compile.AgentCompilationResult;
import com.embabel.agent.spec.model.PromptedActionSpec;
import com.embabel.common.ai.model.LlmOptions;
import com.embabel.smith.common.config.ModelEndpointConfig;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.DraftQuality;
import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.composer.SkillNode;
import com.embabel.smith.lifecycle.LiveEvalRunner;
import com.embabel.smith.nlcompile.NlCompileRunner;
import com.embabel.smith.refinement.RefinementRunner;
import com.embabel.smith.retrieval.scale.LexicalScorer;
import com.embabel.smith.runtime.SkillRunner;
import com.embabel.smith.unified.AuthorController;
import com.embabel.smith.unified.EvalController;
import com.embabel.smith.unified.LifecycleController;
import com.embabel.smith.unified.RunController;
import com.embabel.smith.unified.ScorerBinding;
import com.embabel.smith.unified.SkillCatalog;
import com.embabel.smith.unified.dto.ActionInfo;
import com.embabel.smith.unified.dto.AuthorDraft;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
     * The active retrieval scorer (the shared {@code SkillScorer} seam). Default: the offline lexical
     * scorer. Swap this bean to wire a denser scorer (POC 17/19/20) without touching the panes.
     */
    @Bean
    public ScorerBinding scorerBinding() {
        return new ScorerBinding(new LexicalScorer(), "lexical");
    }

    @Bean
    public RunController.RunFn runFn(AgentPlatform platform, SkillCatalog catalog) {
        return (skillName, input) -> new SkillRunner(platform).run(catalog.dir(skillName), input, null);
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
    public EvalController.EvalFn evalFn(AgentPlatform platform, SkillCatalog catalog,
                                        ModelEndpointConfig endpoint) {
        return (skillName, tasks) -> {
            SkillNode node = catalog.get(skillName);
            SkillPack pack = node.pack();
            RefinementRunner runner = new RefinementRunner(platform);
            RefinementRunner.Endpoint ep = new RefinementRunner.Endpoint(
                    endpoint.baseUrl(), endpoint.apiKey(), endpoint.defaultLlm());
            return runner.improve(pack, pickStep(pack), tasks, ep);
        };
    }

    @Bean
    public LifecycleController.LiveEvalFn liveEvalFn(AgentPlatform platform, SkillCatalog catalog) {
        return (skillName, tasks) ->
                new LiveEvalRunner(platform).evaluate(catalog.get(skillName).pack(), tasks);
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

    private static String pickStep(SkillPack pack) {
        return pack.steps().stream()
                .filter(s -> s.prompt() != null && !s.prompt().isBlank())
                .map(ActionStepSpec::name)
                .findFirst()
                .orElseGet(() -> pack.steps().isEmpty() ? null : pack.steps().get(0).name());
    }
}
