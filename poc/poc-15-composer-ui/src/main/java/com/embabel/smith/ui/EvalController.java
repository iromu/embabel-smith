package com.embabel.smith.ui;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.smith.common.config.ModelEndpointConfig;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.composer.SkillNode;
import com.embabel.smith.refinement.RefinementRunner;
import com.embabel.smith.refinement.SkillEvalTask;
import com.embabel.smith.ui.dto.EvalRequest;
import com.embabel.smith.ui.dto.EvalResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The eval/refinement pane behind the canvas (POC 15): given a skill from the palette, run a POC 5
 * eval over a set of tasks and report the composite score + the failing inputs; when tasks fail,
 * offer a POC 9-style prompt rewrite from the failures and re-score — "a skill can be checked and
 * refined in one place." Endpoint-gated (the skill runs and the rewrite both call the model): without
 * a configured endpoint it returns a clear configuration error before any model call.
 *
 * <p>Delegates the whole loop to POC 9's {@link RefinementRunner} — deploy the skill, evaluate,
 * rewrite the prompt from the failing tasks, re-validate, redeploy, and re-evaluate — so the pane
 * reuses the proven, tested refinement flow rather than reimplementing it.
 */
@RestController
public class EvalController {

    private final SkillCatalog catalog;
    private final AgentPlatform platform;
    private final ModelEndpointConfig endpoint;

    @Autowired
    public EvalController(SkillCatalog catalog,
                          AgentPlatform platform,
                          @Value("${OPENAI_CUSTOM_BASE_URL:}") String baseUrl,
                          @Value("${OPENAI_CUSTOM_API_KEY:}") String apiKey,
                          @Value("${OPENAI_CUSTOM_MODELS:}") String models,
                          @Value("${EMBABEL_MODELS_DEFAULT_LLM:}") String defaultLlm) {
        this.catalog = catalog;
        this.platform = platform;
        this.endpoint = new ModelEndpointConfig(baseUrl, apiKey, models, defaultLlm);
    }

    /** Evaluate (and, on failure, refine) a skill; a clear error when no endpoint is configured. */
    @PostMapping("/api/eval")
    public EvalResponse eval(@RequestBody EvalRequest request) {
        String skillName = request.skillName() == null ? "" : request.skillName();
        if (skillName.isBlank()) {
            return EvalResponse.error("No skill selected; pick a skill to evaluate first.");
        }
        SkillNode node = catalog.get(skillName);
        if (node == null) {
            return EvalResponse.error("Unknown skill: " + skillName + ". Choose one from the palette.");
        }
        String configError = endpoint.configError();
        if (configError != null) {
            return EvalResponse.error(configError);
        }
        SkillPack pack = node.pack();
        String stepName = pickStep(pack);
        List<SkillEvalTask> tasks = request.tasksOrDefault().stream()
                .map(t -> new SkillEvalTask(t.input(), t.acceptanceCriteria(),
                        out -> out != null && !out.isBlank()
                                && out.toLowerCase().contains(t.mustContain().toLowerCase())))
                .toList();
        RefinementRunner runner = new RefinementRunner(platform);
        RefinementRunner.Endpoint ep = new RefinementRunner.Endpoint(
                endpoint.baseUrl(), endpoint.apiKey(), endpoint.defaultLlm());
        RefinementRunner.RefinementOutcome outcome = runner.improve(pack, stepName, tasks, ep);
        return EvalResponse.from(outcome, skillName, stepName);
    }

    /** The step whose prompt to refine: the first step that carries a prompt. */
    private String pickStep(SkillPack pack) {
        return pack.steps().stream()
                .filter(s -> s.prompt() != null && !s.prompt().isBlank())
                .map(ActionStepSpec::name)
                .findFirst()
                .orElseGet(() -> pack.steps().isEmpty() ? null : pack.steps().get(0).name());
    }
}
